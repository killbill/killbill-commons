/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.queue;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import org.killbill.Hostname;
import org.killbill.clock.Clock;
import org.killbill.queue.api.PersistentQueueConfig;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.dao.QueueSqlDao;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class abstract the interaction with the database tables which store the persistent entries for the bus events or
 * notification events.
 * <p/>
 * <p>This can be configured to either cache the recordId for the entries that are ready be fetched so that we avoid expansive
 * queries to the database. Alternatively, the inflight queue is not used and the search query is always run when we need to retrieve
 * new entries.
 *
 * @param <T>
 */
public class DBBackedQueue<T extends org.killbill.queue.dao.EventEntryModelDao> {

    private static final Logger log = LoggerFactory.getLogger(DBBackedQueue.class);

    //
    // Exponential backup retry logic, where we retry up to 10 times for a maximum of about 10 sec ((512 + 256 + 128 + ... ) * 10)
    //
    private final static long INFLIGHT_ENTRIES_INITIAL_POLL_SLEEP_MS = 10;
    private final static int INFLIGHT_ENTRIES_POLL_MAX_RETRY = 10;

    //
    // This is somewhat arbitrary, and could made configurable; the correct value
    // really depends on the size of inflightQ, rate of incoming events, polling interval
    // number of nodes (if non sticky mode), and finally claimed size.
    //
    // The 'expected' use case is to have inflightQ size quite large and be left in a situation
    // where we always restart we only a few elements available in the queue so we
    // start right away with the inflightQ open for read/write.
    //
    private final static int RATIO_INFLIGHT_SIZE_TO_REOPEN_Q_FOR_WRITE = 10;

    //
    // When running with inflightQ, add a polling every 5 minutes to detect if there are
    // entries on disk that are old -- and therefore have been missed. This is purely for
    // for peace of mind and verify the system is healthy.
    //
    private final static long POLLING_ORPHANS_MSEC = (5L * 60L * 1000L);

    private final String DB_QUEUE_LOG_ID;

    private final org.killbill.queue.dao.QueueSqlDao<T> sqlDao;
    private final Clock clock;
    private final PersistentQueueConfig config;

    private final boolean useInflightQueue;
    private final Queue<Long> inflightEvents;
    private final AtomicBoolean isQueueOpenForWrite;
    private final AtomicBoolean isQueueOpenForRead;
    private final int thresholdToReopenQForWrite;

    private final Counter totalInflightInsert;
    private final Counter totalInflightFetched;
    private final Counter totalInsert;
    private final Counter totalFetched;
    private final Counter totalClaimed;
    private final Counter totalProcessedFirstFailures;
    private final Counter totalProcessedSuccess;
    private final Counter totalProcessedAborted;

    private final AtomicLong lastPollingOrphanTime;
    private final AtomicBoolean isRunningOrphanQuery;
    private final AtomicLong lowestOrphanEntry;

    public DBBackedQueue(final Clock clock,
                         final QueueSqlDao<T> sqlDao,
                         final PersistentQueueConfig config,
                         final String dbBackedQId,
                         final MetricRegistry metricRegistry) {
        this.useInflightQueue = config.isUsingInflightQueue();
        this.sqlDao = sqlDao;
        this.config = config;
        this.inflightEvents = useInflightQueue ? new LinkedBlockingQueue<Long>(config.getQueueCapacity()) : null;
        this.isQueueOpenForWrite = new AtomicBoolean(false);
        this.isQueueOpenForRead = new AtomicBoolean(false);
        this.clock = clock;

        //
        // Metrics
        //
        // Number of entries written in the inflightQ since last boot time
        this.totalInflightInsert = metricRegistry.counter(MetricRegistry.name(DBBackedQueue.class, dbBackedQId, "totalInflightInsert"));
        // Number of entries fetched from inflightQ since last boot time (only entries that exist and are aavailable on disk are counted)
        this.totalInflightFetched = metricRegistry.counter(MetricRegistry.name(DBBackedQueue.class, dbBackedQId, "totalInflightFetched"));
        // Number of entries written on disk -- if transaction is rolled back, it is still counted.
        this.totalInsert = metricRegistry.counter(MetricRegistry.name(DBBackedQueue.class, dbBackedQId, "totalInsert"));
        // Number of entries written on disk -- if transaction is rolled back, it is still counted.
        this.totalFetched = metricRegistry.counter(MetricRegistry.name(DBBackedQueue.class, dbBackedQId, "totalFetched"));
        // Number of successfully claimed events
        this.totalClaimed = metricRegistry.counter(MetricRegistry.name(DBBackedQueue.class, dbBackedQId, "totalClaimed"));
        // Number of successfully processed events (move to history table) -- if transaction is rolled back, it is still counted.
        this.totalProcessedSuccess = metricRegistry.counter(MetricRegistry.name(DBBackedQueue.class, dbBackedQId, "totalProcessedSuccess"));
        // Number of first failures for a specific event
        this.totalProcessedFirstFailures = metricRegistry.counter(MetricRegistry.name(DBBackedQueue.class, dbBackedQId, "totalProcessedFirstFailures"));
        //  Number of aborted events
        this.totalProcessedAborted = metricRegistry.counter(MetricRegistry.name(DBBackedQueue.class, dbBackedQId, "totalProcessedAborted"));
        // Export size of inflightQ
        metricRegistry.register(MetricRegistry.name(DBBackedQueue.class, dbBackedQId, "inflightQ", "size"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return useInflightQueue ? inflightEvents.size() : 0;
            }
        });
        metricRegistry.register(MetricRegistry.name(DBBackedQueue.class, dbBackedQId, "inflightQ", "isOpenForRead"), new Gauge<Boolean>() {
            @Override
            public Boolean getValue() {
                return isQueueOpenForRead.get();
            }
        });
        metricRegistry.register(MetricRegistry.name(DBBackedQueue.class, dbBackedQId, "inflightQ", "isOpenForWrite"), new Gauge<Boolean>() {
            @Override
            public Boolean getValue() {
                return isQueueOpenForWrite.get();
            }
        });
        metricRegistry.register(MetricRegistry.name(DBBackedQueue.class, dbBackedQId, "inflightQ", "lowestOrphanEntry"), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return lowestOrphanEntry.get();
            }
        });

        this.thresholdToReopenQForWrite = config.getQueueCapacity() / RATIO_INFLIGHT_SIZE_TO_REOPEN_Q_FOR_WRITE;
        this.lastPollingOrphanTime = new AtomicLong(clock.getUTCNow().getMillis());
        this.isRunningOrphanQuery = new AtomicBoolean(false);
        this.lowestOrphanEntry = new AtomicLong(-1L);

        this.DB_QUEUE_LOG_ID = "DBBackedQueue-" + dbBackedQId + ": ";
    }


    public void initialize() {

        if (useInflightQueue) {
            inflightEvents.clear();
            final List<T> entries = fetchReadyEntries(thresholdToReopenQForWrite);
            if (entries.size() == 0) {
                isQueueOpenForRead.set(true);
                isQueueOpenForWrite.set(true);
            } else {
                isQueueOpenForRead.set(false);
                isQueueOpenForWrite.set(entries.size() < thresholdToReopenQForWrite);
            }
        } else {
            isQueueOpenForRead.set(false);
            isQueueOpenForWrite.set(false);
        }

        // Reset counters.
        totalInflightFetched.dec(totalInflightFetched.getCount());
        totalFetched.dec(totalFetched.getCount());
        totalInflightInsert.dec(totalInflightInsert.getCount());
        totalInsert.dec(totalInsert.getCount());
        totalClaimed.dec(totalClaimed.getCount());
        totalProcessedSuccess.dec(totalProcessedSuccess.getCount());
        totalProcessedFirstFailures.dec(totalProcessedFirstFailures.getCount());
        totalProcessedAborted.dec(totalProcessedAborted.getCount());

        log.info(DB_QUEUE_LOG_ID + "Initialized with isQueueOpenForWrite = " + isQueueOpenForWrite.get() + ", isQueueOpenForRead = " + isQueueOpenForRead.get());
    }


    public void insertEntry(final T entry) {
        sqlDao.inTransaction(new Transaction<Void, QueueSqlDao<T>>() {
            @Override
            public Void inTransaction(final QueueSqlDao<T> transactional, final TransactionStatus status) throws Exception {
                insertEntryFromTransaction(transactional, entry);
                return null;
            }
        });
    }

    public void insertEntryFromTransaction(final QueueSqlDao<T> transactional, final T entry) {

        transactional.insertEntry(entry, config.getTableName());
        totalInsert.inc();

        final Long lastInsertId = transactional.getLastInsertId();
        final boolean isInserted = insertIntoInflightQIfRequired(lastInsertId, entry);
        if (isInserted) {
            totalInflightInsert.inc();
        }
    }


    public List<T> getReadyEntries() {

        List<T> candidates = ImmutableList.<T>of();

        // If we are not configured to use inflightQ then run expensive query
        if (!useInflightQueue) {
            final List<T> entriesToClaim = fetchReadyEntries(config.getMaxEntriesClaimed());
            totalFetched.inc(entriesToClaim.size());
            if (entriesToClaim.size() > 0) {
                candidates = claimEntries(entriesToClaim);
            }
            return candidates;
        }

        if (isQueueOpenForRead.get()) {

            checkForOrphanEntries();

            candidates = fetchReadyEntriesFromIds();
            // There are entries in the Q, we just return those
            if (candidates.size() > 0) {
                totalInflightFetched.inc(candidates.size());
                totalFetched.inc(candidates.size());

                return claimEntries(candidates);
            }

            // There are no more entries in the Q but the Q is not open for write so either there is nothing to be read, or
            // the Q overflowed previously so we disable reading from the Q and continue below.
            if (!isQueueOpenForWrite.get()) {
                final boolean q = isQueueOpenForRead.compareAndSet(true, false);
                if (q) {
                    log.info(DB_QUEUE_LOG_ID + " Closing Q for read");
                }
            }
        }

        if (!isQueueOpenForRead.get()) {
            final int fetchedSize = thresholdToReopenQForWrite > config.getMaxEntriesClaimed() ? thresholdToReopenQForWrite : config.getMaxEntriesClaimed();
            candidates = fetchReadyEntries(fetchedSize);
            // There is a small number so we re-enable adding entries in the Q
            if (candidates.size() < thresholdToReopenQForWrite) {
                boolean r = isQueueOpenForWrite.compareAndSet(false, true);
                if (r) {
                    log.info(DB_QUEUE_LOG_ID + " Opening Q for write");
                }
            }
            if (candidates.size() > config.getMaxEntriesClaimed()) {
                candidates = candidates.subList(0, config.getMaxEntriesClaimed());
            }

            //
            // If we see that we catch up with entries in the inflightQ, we need to switch mode and remove entries we are processing
            // Failure to remove the entries  would NOT trigger a bug, but might waste cycles where getReadyEntries() would return less
            // elements as expected, because entries have already been processed.
            //
            if (removeInflightEventsWhenSwitchingToQueueOpenForRead(candidates)) {
                final boolean q = isQueueOpenForRead.compareAndSet(false, true);
                if (q) {
                    log.info(DB_QUEUE_LOG_ID + " Opening Q for read");
                }
            }

            // Only keep as many candidates as we are allowed to
            totalFetched.inc(candidates.size());
            return claimEntries(candidates);
        }
        return ImmutableList.<T>of();
    }

    private void checkForOrphanEntries() {
        if (clock.getUTCNow().getMillis() > lastPollingOrphanTime.get() + POLLING_ORPHANS_MSEC) {

            if (isRunningOrphanQuery.compareAndSet(false, true)) {
                final List<T> entriesToClaim = fetchReadyEntries(1);
                final Long previousLowestOrphanEntry = lowestOrphanEntry.getAndSet((entriesToClaim.size() == 0) ? -1L : entriesToClaim.get(0).getRecordId());

                lastPollingOrphanTime.set(clock.getUTCNow().getMillis());

                if (previousLowestOrphanEntry > 0 && previousLowestOrphanEntry == lowestOrphanEntry.get()) {
                    log.warn(DB_QUEUE_LOG_ID + "ORPHAN ENTRY FOR RECORD_ID " + previousLowestOrphanEntry + " ?");
                }
                isRunningOrphanQuery.set(false);
            }
        }
    }

    private boolean removeInflightEventsWhenSwitchingToQueueOpenForRead(final List<T> candidates) {

        boolean foundEntryInInflightEvents = false;
        for (T entry : candidates) {
            foundEntryInInflightEvents = inflightEvents.remove(entry.getRecordId());
        }
        return foundEntryInInflightEvents;
    }


    public void updateOnError(final T entry) {
        // We are not (re)incrementing counters totalInflightInsert and totalInsert for these entries, this is a matter of semantics
        sqlDao.inTransaction(new Transaction<Void, QueueSqlDao<T>>() {
            @Override
            public Void inTransaction(final QueueSqlDao<T> transactional, final TransactionStatus status) throws Exception {
                transactional.updateOnError(entry.getRecordId(), clock.getUTCNow().toDate(), entry.getErrorCount(), config.getTableName());
                if (entry.getErrorCount() == 1) {
                    totalProcessedFirstFailures.inc();
                }
                return null;
            }
        });
        insertIntoInflightQIfRequired(entry.getRecordId(), entry);
    }

    public void moveEntryToHistory(final T entry) {
        sqlDao.inTransaction(new Transaction<Void, QueueSqlDao<T>>() {
            @Override
            public Void inTransaction(final QueueSqlDao<T> transactional, final TransactionStatus status) throws Exception {
                moveEntryToHistoryFromTransaction(transactional, entry);
                return null;
            }
        });
    }

    public void moveEntryToHistoryFromTransaction(final QueueSqlDao<T> transactional, final T entry) {
        switch (entry.getProcessingState()) {
            case FAILED:
                totalProcessedAborted.inc();
                break;
            case PROCESSED:
                totalProcessedSuccess.inc();
                break;
            case REMOVED:
                // Don't default for REMOVED since we could call this API 'manually' with that state.
                break;
            default:
                log.warn(DB_QUEUE_LOG_ID + "Unexpected terminal event state " + entry.getProcessingState() + " for record_id = " + entry.getRecordId());
                break;
        }

        if (log.isDebugEnabled()) {
            log.debug(DB_QUEUE_LOG_ID + "Moving entry " + entry.getRecordId() + " into history ");
        }

        transactional.insertEntryWithRecordId(entry, config.getHistoryTableName());
        transactional.removeEntry(entry.getRecordId(), config.getTableName());
    }

    private List<T> fetchReadyEntriesFromIds() {
        final int size = config.getMaxEntriesClaimed() < inflightEvents.size() ? config.getMaxEntriesClaimed() : inflightEvents.size();
        final List<Long> recordIds = new ArrayList<Long>(size);
        for (int i = 0; i < size; i++) {
            final Long entryId = inflightEvents.poll();
            if (entryId != null) {
                recordIds.add(entryId);
            }
        }

        // Before we return we filter on AVAILABLE entries for precaution; the case could potentially happen
        // at the time when we switch from !isQueueOpenForRead -> isQueueOpenForRead with two thread in parallel.
        //
        List<T> result = ImmutableList.<T>of();
        if (recordIds.size() > 0) {

            if (log.isDebugEnabled()) {
                log.debug(DB_QUEUE_LOG_ID + "fetchReadyEntriesFromIds, size = " + size + ", ids = " + Joiner.on(", ").join(recordIds));
            }

            final List<T> entriesFromIds = getEntriesFromIds(recordIds);
            result = ImmutableList.<T>copyOf(Collections2.filter(entriesFromIds, new Predicate<T>() {
                @Override
                public boolean apply(final T input) {
                    return (input.getProcessingState() == PersistentQueueEntryLifecycleState.AVAILABLE);
                }
            }));
        }
        return result;
    }

    private boolean insertIntoInflightQIfRequired(Long lastInsertId, T entry) {
        boolean result = false;
        if (useInflightQueue && isQueueOpenForWrite.get()) {

            result = inflightEvents.offer(lastInsertId);

            if (log.isDebugEnabled()) {
                log.debug(DB_QUEUE_LOG_ID + "Inserting entry " + lastInsertId +
                        (result ? " into inflightQ" : " into disk") +
                        " [" + entry.getEventJson() + "]");
            }

            // Q overflowed
            if (!result) {
                final boolean q = isQueueOpenForWrite.compareAndSet(true, false);
                if (q) {
                    log.info(DB_QUEUE_LOG_ID + "Closing Q for write: Overflowed with recordId = " + lastInsertId);
                }
            }
        }
        return result;
    }

    //
    //  When there are some entries in the inflightQ, 3 cases may have occured:
    //  1. The thread that posted the entry already committed his transaction and in which case the entry
    //     should be found on disk
    //  2. The thread that posted the entry rolled back and therefore that entry will never make it on disk, it should
    //     be ignored.
    //  3. The thread that posted the entry did not complete its transaction and so we don't know whether or not the entry
    //     will make it on disk.
    //
    //  The code below looks for all entries by retrying the lookup on disk, and it eventually returns the one that have been found.
    //  Note that:
    //  - It is is OK for that thread to sleep and retry as this is its nature -- it sleeps and polls
    //  - If for some reason the entry is not found but the transaction eventually commits, we will end up in a situation
    //    where we have entries AVAILABLE on disk; those would be cleared as we restart the service. If this ends up being an issue
    //    we could had some additional logic to catch them.
    //
    private List<T> getEntriesFromIds(final List<Long> recordIds) {

        int originalSize = recordIds.size();
        List<T> result = new ArrayList<T>(recordIds.size());
        int nbTries = 0;

        do {
            final List<T> tmp = sqlDao.getEntriesFromIds(recordIds, config.getTableName());
            if (tmp.size() > 0) {
                for (T cur : tmp) {
                    recordIds.remove(cur.getRecordId());
                }
                result.addAll(tmp);
            }

            if (result.size() < originalSize) {
                try {
                    long sleepTime = INFLIGHT_ENTRIES_INITIAL_POLL_SLEEP_MS * (int) Math.pow(2, nbTries);
                    Thread.sleep(sleepTime);
                    log.info(DB_QUEUE_LOG_ID + "Sleeping " + sleepTime + " for IDS = " + Joiner.on(",").join(recordIds));
                } catch (InterruptedException e) {
                    log.warn(DB_QUEUE_LOG_ID + "Thread " + Thread.currentThread() + " got interrupted");
                    Thread.currentThread().interrupt();
                    return result;
                }
            }
            nbTries++;

        } while (result.size() < originalSize && nbTries < INFLIGHT_ENTRIES_POLL_MAX_RETRY);
        if (recordIds.size() > 0) {
            log.warn(DB_QUEUE_LOG_ID + " Missing inflight entries from disk, recordIds = [" + Joiner.on(",").join(recordIds) + " ]");
        }
        return result;
    }


    private List<T> fetchReadyEntries(int size) {
        final Date now = clock.getUTCNow().toDate();
        final String owner = config.isSticky() ? Hostname.get() : null;
        final List<T> entries = sqlDao.getReadyEntries(now, size, owner, config.getTableName());
        return entries;
    }


    private List<T> claimEntries(List<T> candidates) {
        return ImmutableList.<T>copyOf(Collections2.filter(candidates, new Predicate<T>() {
            @Override
            public boolean apply(final T input) {
                return claimEntry(input);
            }
        }));
    }

    private boolean claimEntry(T entry) {
        final Date nextAvailable = clock.getUTCNow().plus(config.getClaimedTime().getMillis()).toDate();
        final boolean claimed = (sqlDao.claimEntry(entry.getRecordId(), clock.getUTCNow().toDate(), Hostname.get(), nextAvailable, config.getTableName()) == 1);

        if (claimed) {
            totalClaimed.inc();
            if (log.isDebugEnabled()) {
                log.debug(DB_QUEUE_LOG_ID + "Claiming entry " + entry.getRecordId());
            }
        }
        return claimed;
    }

    public QueueSqlDao<T> getSqlDao() {
        return sqlDao;
    }

    public boolean isQueueOpenForWrite() {
        return isQueueOpenForWrite.get();
    }

    public boolean isQueueOpenForRead() {
        return isQueueOpenForRead.get();
    }

    public long getTotalInflightFetched() {
        return totalInflightFetched.getCount();
    }

    public long getTotalFetched() {
        return totalFetched.getCount();
    }

    public long getTotalInflightInsert() {
        return totalInflightInsert.getCount();
    }

    public long getTotalInsert() {
        return totalInsert.getCount();
    }
}
