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
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.killbill.Hostname;
import org.killbill.clock.Clock;
import org.killbill.commons.jdbi.notification.DatabaseTransactionEvent;
import org.killbill.commons.jdbi.notification.DatabaseTransactionEventType;
import org.killbill.commons.jdbi.notification.DatabaseTransactionNotificationApi;
import org.killbill.queue.api.PersistentQueueConfig;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.dao.QueueSqlDao;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
public class DBBackedQueue<T extends org.killbill.queue.dao.EventEntryModelDao> implements Observer {

    private static final Logger log = LoggerFactory.getLogger(DBBackedQueue.class);

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
    private final static long INFLIGHT_POLLING_TIMEOUT_MSEC = 50;

    // Maximum number of events that can be set from within one transaction (we could make it a config param if required)
    private static final int MAX_BUS_ENTRIES_PER_TRANSACTIONS = 3;


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
    private final LinkedBlockingQueue<Long> inflightEvents;
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

    //
    // Per thread information to keep track or recordId while it is accessible and right before
    // transaction gets committed/rollback
    //
    private final static AtomicInteger QUEUE_ID_CNT = new AtomicInteger(0);
    private final int queueId;
    private final TransientInflightQRowIdCache transientInflightQRowIdCache;

    public DBBackedQueue(final Clock clock,
                         final QueueSqlDao<T> sqlDao,
                         final PersistentQueueConfig config,
                         final String dbBackedQId,
                         final MetricRegistry metricRegistry,
                         @Nullable final DatabaseTransactionNotificationApi databaseTransactionNotificationApi) {
        this.queueId = QUEUE_ID_CNT.incrementAndGet();
        this.useInflightQueue = config.isUsingInflightQueue();
        this.sqlDao = sqlDao;
        this.config = config;
        this.inflightEvents = useInflightQueue ? new LinkedBlockingQueue<Long>(config.getQueueCapacity()) : null;
        this.isQueueOpenForWrite = new AtomicBoolean(false);
        this.isQueueOpenForRead = new AtomicBoolean(false);
        this.clock = clock;
        if (useInflightQueue && databaseTransactionNotificationApi != null) {
            databaseTransactionNotificationApi.registerForNotification(this);
        }

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
        this.transientInflightQRowIdCache = useInflightQueue ? new TransientInflightQRowIdCache(queueId) : null;
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

        log.info(DB_QUEUE_LOG_ID + "Initialized with useInflightQueue = " + useInflightQueue +
                ", queueId = " + queueId +
                ", isSticky = " + config.isSticky() +
                ", isQueueOpenForWrite = " + isQueueOpenForWrite.get() +
                ", isQueueOpenForRead = " + isQueueOpenForRead.get());
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

        // LAST_INSERT_ID is kept at the transaction level; we reset it to 0 so that in case insert fails, we don't end up with a previous
        // value that would end up corrupting the inflightQ
        // Note! This is a no-op for H2 (see QueueSqlDao.sql.stg and https://github.com/killbill/killbill/issues/223)
        transactional.resetLastInsertId();
        transactional.insertEntry(entry, config.getTableName());
        final Long lastInsertId = transactional.getLastInsertId();
        if (lastInsertId == 0) {
            log.warn(DB_QUEUE_LOG_ID + "Failed to insert entry, lastInsertedId " + lastInsertId);
            return;
        }

        // The current thread is in the middle of  a transaction and this is the only times it knows about the recordId for the queue event;
        // It keeps track of it as a per thread data. Very soon, when the transaction gets committed/rolled back it can then extract the info
        // and insert the recordId into a blockingQ that is highly optimized to dispatch events.
        if (useInflightQueue && isQueueOpenForWrite.get()) {
            transientInflightQRowIdCache.addRowId(lastInsertId);
            //log.info(DB_QUEUE_LOG_ID + "Setting for thread " + Thread.currentThread().getId() + ", row = " + lastInsertId);
        }
        totalInsert.inc();
    }

    public List<T> getReadyEntries() {

        List<T> candidates = ImmutableList.<T>of();

        // If we are not configured to use inflightQ then run select query; also we synchronize the block
        // because there si no point having two concurrent threads racing each other, with only of of which
        // being able to claim the entries.
        //
        if (!useInflightQueue) {
            synchronized (this) {
                final List<T> entriesToClaim = fetchReadyEntries(config.getMaxEntriesClaimed());
                totalFetched.inc(entriesToClaim.size());
                if (entriesToClaim.size() > 0) {
                    candidates = claimEntries(entriesToClaim);
                }
                return candidates;
            }
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

        // There is no entry and yet Q is open for write so we can safely start reading from Q
        if (candidates.size() == 0) {
            return true;
        }

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
                if (useInflightQueue) {
                    transientInflightQRowIdCache.addRowId(entry.getRecordId());
                }
                return null;
            }
        });
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
        try {
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
        } catch (final Exception e) {
            log.warn(DB_QUEUE_LOG_ID + "Failed to move entries [" + entry.getRecordId() + "] into history ", e);
        }
    }

    public void moveEntriesToHistory(final Iterable<T> entries) {
        try {
            sqlDao.inTransaction(new Transaction<Void, QueueSqlDao<T>>() {
                @Override
                public Void inTransaction(final QueueSqlDao<T> transactional, final TransactionStatus status) throws Exception {
                    moveEntriesToHistoryFromTransaction(transactional, entries);
                    return null;
                }
            });
        } catch (final Exception e) {
            final Iterable<Long> recordIds = Iterables.transform(entries, new Function<T, Long>() {
                @Nullable
                @Override
                public Long apply(@Nullable T input) {
                    return input.getRecordId();
                }
            });
            log.warn(DB_QUEUE_LOG_ID + "Failed to move entries [" + Joiner.on(", ").join(recordIds) + "] into history ", e);
        }
    }

    private void moveEntriesToHistoryFromTransaction(final QueueSqlDao<T> transactional, final Iterable<T> entries) {

        if (!entries.iterator().hasNext()) {
            return;
        }

        for (T cur : entries) {
            switch (cur.getProcessingState()) {
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
                    log.warn(DB_QUEUE_LOG_ID + "Unexpected terminal event state " + cur.getProcessingState() + " for record_id = " + cur.getRecordId());
                    break;
            }
            if (log.isDebugEnabled()) {
                log.debug(DB_QUEUE_LOG_ID + "Moving entry " + cur.getRecordId() + " into history ");
            }
        }

        final Iterable toBeRemovedRecordIds = Iterables.transform(entries, new Function<T, Object>() {
            @Override
            public Object apply(T input) {
                return input.getRecordId();
            }
        });

        transactional.insertEntriesWithRecordId(entries, config.getHistoryTableName());
        transactional.removeEntries(ImmutableList.copyOf(toBeRemovedRecordIds), config.getTableName());
    }


    private List<T> fetchReadyEntriesFromIds() {
        //
        // We want to fetch no more than max requested (getMaxInflightQEntriesClaimed) OR size of the queue
        // However if there is nothing we also want to block the thread so it is awoken on the very first ready event instead or retuning
        // and polling (sleeping).
        //
        final int size = config.getMaxInflightQEntriesClaimed() < inflightEvents.size() ? config.getMaxInflightQEntriesClaimed() : inflightEvents.size();
        final int nonZeroSize = size == 0 ? 1 : size;
        final List<Long> recordIds = new ArrayList<Long>(nonZeroSize);
        for (int i = 0; i < nonZeroSize; i++) {
            final Long entryId;
            try {
                entryId = size == 0 ?
                        inflightEvents.poll(INFLIGHT_POLLING_TIMEOUT_MSEC, TimeUnit.MILLISECONDS) : // There seems be nothing in the Q so we block
                        inflightEvents.poll(); // The queue does not seem empty so we don't want to block for no reason if there is less entries than detected.
                if (entryId == null) {
                    break;
                }
                recordIds.add(entryId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn(DB_QUEUE_LOG_ID + "Got interrupted ");
                return ImmutableList.of();
            }
        }

        // Before we return we filter on AVAILABLE entries for precaution; the case could potentially happen
        // at the time when we switch from !isQueueOpenForRead -> isQueueOpenForRead with two thread in parallel.
        //
        List<T> result = ImmutableList.<T>of();
        if (recordIds.size() > 0) {

            if (log.isDebugEnabled()) {
                log.debug(DB_QUEUE_LOG_ID + "fetchReadyEntriesFromIds, size = " + nonZeroSize + ", ids = " + Joiner.on(", ").join(recordIds));
            }

            final List<T> entriesFromIds = sqlDao.getEntriesFromIds(recordIds, config.getTableName());
            result = ImmutableList.<T>copyOf(Collections2.filter(entriesFromIds, new Predicate<T>() {
                @Override
                public boolean apply(final T input) {
                    return (input.getProcessingState() == PersistentQueueEntryLifecycleState.AVAILABLE);
                }
            }));
        }
        return result;
    }

    private List<T> fetchReadyEntries(int size) {
        final Date now = clock.getUTCNow().toDate();
        final String owner = config.isSticky() ? Hostname.get() : null;
        final List<T> entries = sqlDao.getReadyEntries(now, size, owner, config.getTableName());
        return entries;
    }

    private List<T> claimEntries(final List<T> candidates) {
        if (config.isSticky()) {
            return batchClaimEntries(candidates);
        } else {
            return sequentialClaimEntries(candidates);
        }
    }

    //
    // In sticky mode, we can batch claim update; however we want to avoid two concurrent threads to run the same query
    // at the same time to realize it cannot claim any entries before of timing issue -- see synchronized statement in getReadyEntries
    private List<T> batchClaimEntries(List<T> candidates) {
        if (candidates.size() == 0) {
            return ImmutableList.of();
        }
        final Date nextAvailable = clock.getUTCNow().plus(config.getClaimedTime().getMillis()).toDate();
        final Collection<Long> recordIds = Collections2.transform(candidates, new Function<T, Long>() {
            @Override
            public Long apply(T input) {
                return input.getRecordId();
            }
        });
        final int resultCount = sqlDao.claimEntries(recordIds, clock.getUTCNow().toDate(), Hostname.get(), nextAvailable, config.getTableName());
        if (resultCount == candidates.size()) {
            totalClaimed.inc(resultCount);
            return candidates;
        }

        final List<T> maybeClaimedEntries = sqlDao.getEntriesFromIds(ImmutableList.copyOf(recordIds), config.getTableName());
        final Iterable claimed = Iterables.filter(maybeClaimedEntries, new Predicate<T>() {
            @Override
            public boolean apply(T input) {
                return input.getProcessingState() == PersistentQueueEntryLifecycleState.IN_PROCESSING && input.getProcessingOwner().equals(Hostname.get());
            }
        });
        final List<T> result = ImmutableList.copyOf(claimed);
        totalClaimed.inc(result.size());
        return result;
    }

    //
    // In non sticky mode, we don't optimize claim update because we can't synchronize easily -- we could rely on global lock,
    // but we are looking for performance and that does not the right choice.
    //
    private List<T> sequentialClaimEntries(List<T> candidates) {
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

    @Override
    public void update(Observable o, Object arg) {

        final DatabaseTransactionEvent event = (DatabaseTransactionEvent) arg;

        // Either a transaction we are not interested in, or for the wrong queue; just return.
        if (transientInflightQRowIdCache == null || !transientInflightQRowIdCache.isValid()) {
            return;
        }

        // This is a ROLLBACK, clear the threadLocal and return
        if (event.getType() == DatabaseTransactionEventType.ROLLBACK) {
            transientInflightQRowIdCache.reset();
            return;
        }

        try {
            // Add entry in the inflightQ and clear threadlocal
            final Iterator<Long> entries = transientInflightQRowIdCache.iterator();
            while (entries.hasNext()) {
                final Long entry = entries.next();
                final boolean result = inflightEvents.offer(entry);
                if (result) {
                    if (log.isDebugEnabled()) {
                        log.debug(DB_QUEUE_LOG_ID + "Inserting entry " + entry +
                                (result ? " into inflightQ" : " into disk"));
                    }
                    totalInflightInsert.inc();
                    // Q overflowed ?
                } else {
                    final boolean q = isQueueOpenForWrite.compareAndSet(true, false);
                    if (q) {
                        log.info(DB_QUEUE_LOG_ID + "Closing Q for write: Overflowed with recordId = " + entry);
                    }
                }
            }
        } finally {
            transientInflightQRowIdCache.reset();
        }
    }

    //
    // Hide the ThreadLocal logic required for inflightQ algorithm in that class and export an easy to use interface.
    //
    private static class TransientInflightQRowIdCache {

        private final ThreadLocal<RowRef> rowRefThreadLocal = new ThreadLocal<RowRef>();
        private final int queueId;

        private TransientInflightQRowIdCache(int queueId) {
            this.queueId = queueId;
        }

        public boolean isValid() {
            final RowRef entry = rowRefThreadLocal.get();
            return (entry != null && entry.queueId == queueId);
        }

        public void addRowId(final Long rowId) {
            RowRef entry = rowRefThreadLocal.get();
            if (entry == null) {
                entry = new RowRef(queueId, rowId);
                rowRefThreadLocal.set(entry);
            } else {
                entry.addRowId(rowId);
            }
        }

        public void reset() {
            rowRefThreadLocal.remove();
        }

        public Iterator<Long> iterator() {
            final RowRef entry = rowRefThreadLocal.get();
            Preconditions.checkNotNull(entry);
            return entry.iterator();
        }


        // Internal structure to keep track or recordId per queue
        private final class RowRef {

            private final int queueId;
            private final long[] rowIds;

            private int offset;

            public RowRef(int queueId, long initialRowId) {
                this.queueId = queueId;
                this.rowIds = new long[MAX_BUS_ENTRIES_PER_TRANSACTIONS];
                this.offset = 0;
                this.rowIds[offset] = initialRowId;
            }

            public void addRowId(long rowId) {
                if (offset == MAX_BUS_ENTRIES_PER_TRANSACTIONS - 1) {
                    log.error("DBBackedQ- Thread " + Thread.currentThread().getId() + "DBBackedQueue InflightQ thread local variable for queue " + queueId + " has too many entries, and was probably not reset correctly! offset = " + offset);
                    return;
                }
                rowIds[++offset] = rowId;
            }

            public Iterator<Long> iterator() {
                return new Iterator<Long>() {

                    private int iteratorOffset = 0;

                    @Override
                    public boolean hasNext() {
                        return (iteratorOffset <= offset);
                    }

                    @Override
                    public Long next() {
                        return rowIds[iteratorOffset++];
                    }

                    @Override
                    public void remove() {
                        throw new IllegalStateException();
                    }
                };
            }
        }
    }
}
