/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2015-2018 Groupon, Inc
 * Copyright 2015-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.queue;

import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.clock.Clock;
import org.killbill.commons.jdbi.notification.DatabaseTransactionEvent;
import org.killbill.commons.jdbi.notification.DatabaseTransactionEventType;
import org.killbill.commons.jdbi.notification.DatabaseTransactionNotificationApi;
import org.killbill.commons.profiling.Profiling;
import org.killbill.commons.profiling.ProfilingFeature;
import org.killbill.queue.api.PersistentQueueConfig;
import org.killbill.queue.api.PersistentQueueConfig.PersistentQueueMode;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.dao.EventEntryModelDao;
import org.killbill.queue.dao.QueueSqlDao;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

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
public class DBBackedQueue<T extends EventEntryModelDao> {

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
    private static final int RATIO_INFLIGHT_SIZE_TO_REOPEN_Q_FOR_WRITE = 10;

    private static final int MAX_FETCHED_ENTRIES = 100;

    private static final long INFLIGHT_POLLING_TIMEOUT_MSEC = 100;


    //
    // When running with inflightQ, add a polling every 5 minutes to detect if there are
    // entries on disk that are old -- and therefore have been missed. This is purely for
    // for peace of mind and verify the system is healthy.
    //
    private static final long POLLING_ORPHANS_MSEC = (5L * 60L * 1000L);

    private final String DB_QUEUE_LOG_ID;

    private final IDBI dbi;
    private final Class<? extends QueueSqlDao<T>> sqlDaoClass;
    private final QueueSqlDao<T> sqlDao;
    private final Clock clock;
    private final PersistentQueueConfig config;

    private final boolean useInflightQueue;
    private final LinkedBlockingQueue<Long> inflightEvents;
    private final int thresholdToReopenQForWrite;

    private final Counter totalInflightInsert;
    private final Counter totalInflightFetched;
    private final Counter totalInsert;
    private final Counter totalFetched;
    private final Counter totalClaimed;
    private final Counter totalProcessedFirstFailures;
    private final Counter totalProcessedSuccess;
    private final Counter totalProcessedAborted;

    private final Profiling<Long, RuntimeException> prof;

    private volatile boolean isQueueOpenForWrite;
    private volatile boolean isQueueOpenForRead;

    private long lastPollingOrphanTime;
    private long lowestOrphanEntry;

    //
    // Per thread information to keep track or recordId while it is accessible and right before
    // transaction gets committed/rollback
    //
    private static final AtomicInteger QUEUE_ID_CNT = new AtomicInteger(0);
    private final int queueId;
    private final TransientInflightQRowIdCache transientInflightQRowIdCache;

    public DBBackedQueue(final Clock clock,
                         final IDBI dbi,
                         final Class<? extends QueueSqlDao<T>> sqlDaoClass,
                         final PersistentQueueConfig config,
                         final String dbBackedQId,
                         final MetricRegistry metricRegistry,
                         @Nullable final DatabaseTransactionNotificationApi databaseTransactionNotificationApi) {
        this.queueId = QUEUE_ID_CNT.incrementAndGet();
        this.useInflightQueue = config.getPersistentQueueMode() == PersistentQueueMode.STICKY_EVENTS;
        this.dbi = dbi;
        this.sqlDaoClass = sqlDaoClass;
        this.sqlDao = dbi.onDemand(sqlDaoClass);
        this.config = config;
        this.inflightEvents = useInflightQueue ? new LinkedBlockingQueue<Long>(config.getEventQueueCapacity()) : null;
        this.isQueueOpenForWrite = false;
        this.isQueueOpenForRead = false;
        this.clock = clock;
        this.prof = new Profiling<Long, RuntimeException>();
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
        // Number of entries fetched from disk -- if transaction is rolled back, it is still counted.
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
                return isQueueOpenForRead;
            }
        });
        metricRegistry.register(MetricRegistry.name(DBBackedQueue.class, dbBackedQId, "inflightQ", "isOpenForWrite"), new Gauge<Boolean>() {
            @Override
            public Boolean getValue() {
                return isQueueOpenForWrite;
            }
        });
        metricRegistry.register(MetricRegistry.name(DBBackedQueue.class, dbBackedQId, "inflightQ", "lowestOrphanEntry"), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return lowestOrphanEntry;
            }
        });

        this.thresholdToReopenQForWrite = config.getEventQueueCapacity() / RATIO_INFLIGHT_SIZE_TO_REOPEN_Q_FOR_WRITE;
        this.lastPollingOrphanTime = clock.getUTCNow().getMillis();
        this.lowestOrphanEntry = -1L;
        this.transientInflightQRowIdCache = useInflightQueue ? new TransientInflightQRowIdCache(queueId) : null;
        this.DB_QUEUE_LOG_ID = "DBBackedQueue-" + dbBackedQId;
    }

    public void initialize() {
        if (useInflightQueue) {
            inflightEvents.clear();
            final List<T> entries = fetchReadyEntries(thresholdToReopenQForWrite);
            if (entries.isEmpty()) {
                isQueueOpenForRead = true;
                isQueueOpenForWrite = true;
            } else {
                isQueueOpenForRead = false;
                isQueueOpenForWrite = entries.size() < thresholdToReopenQForWrite;
            }
        } else {
            isQueueOpenForRead = false;
            isQueueOpenForWrite = false;
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

        log.info("{} Initialized with queueId={}, mode={}, isQueueOpenForWrite={}, isQueueOpenForRead={}",
                 DB_QUEUE_LOG_ID, queueId, config.getPersistentQueueMode(), isQueueOpenForWrite, isQueueOpenForRead);
    }

    public void insertEntry(final T entry) {
        executeTransaction(new Transaction<Void, QueueSqlDao<T>>() {
            @Override
            public Void inTransaction(final QueueSqlDao<T> transactional, final TransactionStatus status) {
                insertEntryFromTransaction(transactional, entry);
                return null;
            }
        });
    }

    public void insertEntryFromTransaction(final QueueSqlDao<T> transactional, final T entry) {
        final Long lastInsertId = safeInsertEntry(transactional, entry);
        if (lastInsertId == 0) {
            log.warn("{} Failed to insert entry, lastInsertedId={}", DB_QUEUE_LOG_ID, lastInsertId);
            return;
        }

        // The current thread is in the middle of  a transaction and this is the only times it knows about the recordId for the queue event;
        // It keeps track of it as a per thread data. Very soon, when the transaction gets committed/rolled back it can then extract the info
        // and insert the recordId into a blockingQ that is highly optimized to dispatch events.
        if (useInflightQueue && isQueueOpenForWrite) {
            transientInflightQRowIdCache.addRowId(lastInsertId);
        }
        totalInsert.inc();
    }

    public List<T> getReadyEntries() {
        if (useInflightQueue) {
            return getReadyEntriesUsingInflightQueue();
        } else {
            return getReadyEntriesUsingPollingMode();
        }
    }

    private List<T> getReadyEntriesUsingPollingMode() {
        final List<T> entriesToClaim = fetchReadyEntries(config.getMaxEntriesClaimed());
        totalFetched.inc(entriesToClaim.size());
        if (!entriesToClaim.isEmpty()) {
            log.debug("{} Entries to claim: {}", DB_QUEUE_LOG_ID, entriesToClaim);
            return claimEntries(entriesToClaim);
        }
        return ImmutableList.<T>of();
    }

    private List<T> getReadyEntriesUsingInflightQueue() {
        List<T> candidates;
        if (isQueueOpenForRead) {

            checkForOrphanEntries();

            candidates = fetchReadyEntriesFromIds();
            // There are entries in the Q, we just return those
            if (!candidates.isEmpty()) {
                totalInflightFetched.inc(candidates.size());
                totalFetched.inc(candidates.size());
                // There is no need to claim entries in the mode as the thread holding the records is the only one which had access to the ids
                return candidates;
            }

            // There are no more entries in the Q but the Q is not open for write so either there is nothing to be read, or
            // the Q overflowed previously so we disable reading from the Q and continue below.
            if (!isQueueOpenForWrite) {
                isQueueOpenForRead = false;
                log.info("{} Closing Q for read", DB_QUEUE_LOG_ID);
            }
        }

        if (!isQueueOpenForRead) {
            candidates = fetchReadyEntries(config.getMaxEntriesClaimed());
            //
            // There is a small number so we re-enable adding entries in the Q
            // We optimize by first checking if the number of entries is smaller than config.getMaxEntriesClaimed()
            // and if not then we perform the query (we could even optimize more by only performing that query with less frequency)
            //
            if (!isQueueOpenForWrite &&
                    (candidates.size() < config.getMaxEntriesClaimed() ||
                            (getNbReadyEntries() < thresholdToReopenQForWrite))) {
                isQueueOpenForWrite = true;
                log.info("{} Opening Q for write", DB_QUEUE_LOG_ID);
            }

            //
            // If we see that we catch up with entries in the inflightQ, we need to switch mode and remove entries we are processing
            // Failure to remove the entries  would NOT trigger a bug, but might waste cycles where getReadyEntries() would return less
            // elements as expected, because entries have already been processed.
            //
            if (removeInflightEventsWhenSwitchingToQueueOpenForRead(candidates)) {
                isQueueOpenForRead = true;
                log.info("{} Opening Q for read", DB_QUEUE_LOG_ID);
            }

            // Only keep as many candidates as we are allowed to
            totalFetched.inc(candidates.size());
            return claimEntries(candidates);
        }
        return ImmutableList.<T>of();
    }

    private void checkForOrphanEntries() {
        if (clock.getUTCNow().getMillis() > lastPollingOrphanTime + POLLING_ORPHANS_MSEC) {

            final List<T> entriesToClaim = fetchReadyEntries(1);
            final Long previousLowestOrphanEntry = lowestOrphanEntry;
            lowestOrphanEntry = (entriesToClaim.isEmpty()) ? -1L : entriesToClaim.get(0).getRecordId();
            if (previousLowestOrphanEntry > 0 && previousLowestOrphanEntry == lowestOrphanEntry) {
                log.warn("{} Detected unprocessed bus event {}", DB_QUEUE_LOG_ID, previousLowestOrphanEntry);
            }

            lastPollingOrphanTime = clock.getUTCNow().getMillis();
        }
    }

    private boolean removeInflightEventsWhenSwitchingToQueueOpenForRead(final List<T> candidates) {
        // There is no entry and yet Q is open for write so we can safely start reading from Q
        if (candidates.isEmpty()) {
            return true;
        }

        boolean foundAllEntriesInInflightEvents = true;

        final List<Long> entries = new ArrayList<Long>(candidates.size());
        for (final T entry : candidates) {
            entries.add(entry.getRecordId());
            final boolean found = inflightEvents.remove(entry.getRecordId());
            if (!found) {
                foundAllEntriesInInflightEvents = false;
            }
        }
        return foundAllEntriesInInflightEvents;
    }

    public void updateOnError(final T entry) {
        // We are not (re)incrementing counters totalInflightInsert and totalInsert for these entries, this is a matter of semantics
        executeTransaction(new Transaction<Void, QueueSqlDao<T>>() {
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
        executeTransaction(new Transaction<Void, QueueSqlDao<T>>() {
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
                    log.warn("{} Unexpected terminal event state={} for record_id={}", DB_QUEUE_LOG_ID, entry.getProcessingState(), entry.getRecordId());
                    break;
            }

            log.debug("{} Moving entry into history: recordId={}, className={}, json={}", DB_QUEUE_LOG_ID, entry.getRecordId(), entry.getClassName(), entry.getEventJson());

            transactional.insertEntry(entry, config.getHistoryTableName());
            transactional.removeEntry(entry.getRecordId(), config.getTableName());
        } catch (final Exception e) {
            log.warn("{} Failed to move entry into history: {}", DB_QUEUE_LOG_ID, entry, e);
        }
    }

    public void moveEntriesToHistory(final Iterable<T> entries) {
        try {
            executeTransaction(new Transaction<Void, QueueSqlDao<T>>() {
                @Override
                public Void inTransaction(final QueueSqlDao<T> transactional, final TransactionStatus status) throws Exception {
                    moveEntriesToHistoryFromTransaction(transactional, entries);
                    return null;
                }
            });
        } catch (final Exception e) {
            log.warn("{} Failed to move entries into history: {}", DB_QUEUE_LOG_ID, entries, e);
        }
    }

    private void moveEntriesToHistoryFromTransaction(final QueueSqlDao<T> transactional, final Iterable<T> entries) {
        if (!entries.iterator().hasNext()) {
            return;
        }

        for (final T cur : entries) {
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
                    log.warn("{} Unexpected terminal event state={} for record_id={}", DB_QUEUE_LOG_ID, cur.getProcessingState(), cur.getRecordId());
                    break;
            }
            log.debug("{} Moving entry into history: recordId={}, className={}, json={}", DB_QUEUE_LOG_ID, cur.getRecordId(), cur.getClassName(), cur.getEventJson());
        }

        final Iterable<Long> toBeRemovedRecordIds = Iterables.<T, Long>transform(entries, new Function<T, Long>() {
            @Override
            public Long apply(final T input) {
                return input.getRecordId();
            }
        });

        transactional.insertEntries(entries, config.getHistoryTableName());
        transactional.removeEntries(ImmutableList.<Long>copyOf(toBeRemovedRecordIds), config.getTableName());
    }

    private List<T> fetchReadyEntriesFromIds() {
        // Drain the inflightEvents queue up to a maximum (MAX_FETCHED_ENTRIES)
        final List<Long> recordIds = new ArrayList<Long>(MAX_FETCHED_ENTRIES);
        inflightEvents.drainTo(recordIds, MAX_FETCHED_ENTRIES);
        if (recordIds.isEmpty()) {
            try {
                // We block until we see the first entry or reach the timeout (in which case we will rerun the doProcessEvents() loop and come back here).
                final Long entryId = inflightEvents.poll(INFLIGHT_POLLING_TIMEOUT_MSEC, TimeUnit.MILLISECONDS);
                if (entryId != null) {
                    recordIds.add(entryId);
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("{} Got interrupted ", DB_QUEUE_LOG_ID);
                return ImmutableList.of();
            }
        }

        if (!recordIds.isEmpty()) {
            log.debug("{} fetchReadyEntriesFromIds: {}", DB_QUEUE_LOG_ID, recordIds);

            return executeQuery(new Query<List<T>, QueueSqlDao<T>>() {
                @Override
                public List<T> execute(final QueueSqlDao<T> queueSqlDao) {
                    return queueSqlDao.getEntriesFromIds(recordIds, config.getTableName());
                }
            });
        }
        return ImmutableList.<T>of();
    }

    private List<T> fetchReadyEntries(final int size) {
        final Date now = clock.getUTCNow().toDate();
        final String owner = config.getPersistentQueueMode() == PersistentQueueMode.POLLING ? null : CreatorName.get();
        return executeQuery(new Query<List<T>, QueueSqlDao<T>>() {
            @Override
            public List<T> execute(final QueueSqlDao<T> queueSqlDao) {
                return queueSqlDao.getReadyEntries(now, size, owner, config.getTableName());
            }
        });
    }

    private long getNbReadyEntries() {
        final Date now = clock.getUTCNow().toDate();
        return getNbReadyEntries(now);
    }

    public long getNbReadyEntries(final Date now) {
        final String owner = config.getPersistentQueueMode() == PersistentQueueMode.POLLING ? null : CreatorName.get();
        return executeQuery(new Query<Long, QueueSqlDao<T>>() {
            @Override
            public Long execute(final QueueSqlDao<T> queueSqlDao) {
                return queueSqlDao.getNbReadyEntries(now, owner, config.getTableName());
            }
        });
    }

    private List<T> claimEntries(final List<T> candidates) {
        switch (config.getPersistentQueueMode()) {
            case POLLING:
                return sequentialClaimEntries(candidates);

            case STICKY_POLLING:
                // There is no claiming in STICKY_EVENTS mode except when the inflightQ overflow and we revert to STICKY_POLLING
            case STICKY_EVENTS:
                return batchClaimEntries(candidates);

            default:
                throw new IllegalStateException("Unsupported PersistentQueueMode " + config.getPersistentQueueMode());
        }
    }

    private List<T> batchClaimEntries(final List<T> candidates) {
        if (candidates.isEmpty()) {
            return ImmutableList.of();
        }
        final Date nextAvailable = clock.getUTCNow().plus(config.getClaimedTime().getMillis()).toDate();
        final Collection<Long> recordIds = Collections2.transform(candidates, new Function<T, Long>() {
            @Override
            public Long apply(final T input) {
                return input.getRecordId();
            }
        });
        final int resultCount = executeQuery(new Query<Integer, QueueSqlDao<T>>() {
            @Override
            public Integer execute(final QueueSqlDao<T> queueSqlDao) {
                return queueSqlDao.claimEntries(recordIds, clock.getUTCNow().toDate(), CreatorName.get(), nextAvailable, config.getTableName());
            }
        });
        // We should ALWAYS see the same number since we are in STICKY_POLLING mode and there is only one thread claiming entries.
        // We keep the 2 cases below for safety (code was written when this was MT-threaded), and we log with warn (will eventually remove it in the future)
        if (resultCount == candidates.size()) {
            totalClaimed.inc(resultCount);
            log.debug("{} batchClaimEntries claimed: {}", DB_QUEUE_LOG_ID, candidates);
            return candidates;
            // Nothing... the synchronized block let go another concurrent thread
        } else if (resultCount == 0) {
            log.warn("{} batchClaimEntries see 0 entries", DB_QUEUE_LOG_ID);
            return ImmutableList.of();
        } else {
            final List<T> maybeClaimedEntries = executeQuery(new Query<List<T>, QueueSqlDao<T>>() {
                @Override
                public List<T> execute(final QueueSqlDao<T> queueSqlDao) {
                    return queueSqlDao.getEntriesFromIds(ImmutableList.copyOf(recordIds), config.getTableName());
                }
            });
            final Iterable<T> claimed = Iterables.<T>filter(maybeClaimedEntries, new Predicate<T>() {
                @Override
                public boolean apply(final T input) {
                    return input.getProcessingState() == PersistentQueueEntryLifecycleState.IN_PROCESSING && input.getProcessingOwner().equals(CreatorName.get());
                }
            });

            final List<T> result = ImmutableList.<T>copyOf(claimed);

            log.warn("{} batchClaimEntries only claimed partial entries {}/{}", DB_QUEUE_LOG_ID, result.size(), candidates.size());

            totalClaimed.inc(result.size());
            return result;
        }
    }

    //
    // In non sticky mode, we don't optimize claim update because we can't synchronize easily -- we could rely on global lock,
    // but we are looking for performance and that does not the right choice.
    //
    private List<T> sequentialClaimEntries(final List<T> candidates) {
        return ImmutableList.<T>copyOf(Collections2.filter(candidates, new Predicate<T>() {
            @Override
            public boolean apply(final T input) {
                return claimEntry(input);
            }
        }));
    }

    private boolean claimEntry(final T entry) {
        final Date nextAvailable = clock.getUTCNow().plus(config.getClaimedTime().getMillis()).toDate();
        final int claimEntry = executeQuery(new Query<Integer, QueueSqlDao<T>>() {
            @Override
            public Integer execute(final QueueSqlDao<T> queueSqlDao) {
                return queueSqlDao.claimEntry(entry.getRecordId(), clock.getUTCNow().toDate(), CreatorName.get(), nextAvailable, config.getTableName());
            }
        });
        final boolean claimed = (claimEntry == 1);

        if (claimed) {
            totalClaimed.inc();
            log.debug("{} Claimed entry {}", DB_QUEUE_LOG_ID, entry);
        }
        return claimed;
    }

    public QueueSqlDao<T> getSqlDao() {
        return sqlDao;
    }

    public boolean isQueueOpenForWrite() {
        return isQueueOpenForWrite;
    }

    public boolean isQueueOpenForRead() {
        return isQueueOpenForRead;
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

    @AllowConcurrentEvents
    @Subscribe
    public void handleDatabaseTransactionEvent(final DatabaseTransactionEvent event) {
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
                    log.debug("{} Inserting entry {} into inflightQ", DB_QUEUE_LOG_ID, entry);
                    totalInflightInsert.inc();
                    // Q overflowed, which means we will stop writing entries into the Q, and as a result, we will end up stop reading
                    // from the Q and return to polling mode
                } else if (isQueueOpenForWrite) {
                    isQueueOpenForWrite = false;
                    log.warn("{} Closing Q for write: Overflowed with recordId={}", DB_QUEUE_LOG_ID, entry);
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

        private TransientInflightQRowIdCache(final int queueId) {
            this.queueId = queueId;
        }

        public boolean isValid() {
            final RowRef entry = rowRefThreadLocal.get();
            return (entry != null && entry.queueId == queueId);
        }

        public void addRowId(final Long rowId) {
            RowRef entry = rowRefThreadLocal.get();
            if (entry == null) {
                entry = new RowRef(queueId);
                rowRefThreadLocal.set(entry);
            }
            entry.addRowId(rowId);
        }

        public void reset() {
            rowRefThreadLocal.remove();
        }

        public Iterator<Long> iterator() {
            final RowRef entry = rowRefThreadLocal.get();
            Preconditions.checkNotNull(entry);
            return entry.iterator();
        }


        // Internal structure to keep track of recordId per queue
        private final class RowRef {

            private final int queueId;
            private final List<Long> rowIds;

            public RowRef(final int queueId) {
                this.queueId = queueId;
                this.rowIds = new ArrayList<Long>();
            }

            public void addRowId(final long rowId) {
                rowIds.add(rowId);
            }

            public Iterator<Long> iterator() {
                return rowIds.iterator();
            }
        }
    }

    private Long safeInsertEntry(final QueueSqlDao<T> transactional, final T entry) {
        return prof.executeWithProfiling(ProfilingFeature.ProfilingFeatureType.DAO, "QueueSqlDao:insert", new Profiling.WithProfilingCallback<Long, RuntimeException>() {

            @Override
            public Long execute() throws RuntimeException {
                // LAST_INSERT_ID is kept at the transaction level; we reset it to 0 so that in case insert fails, we don't end up with a previous
                // value that would end up corrupting the inflightQ
                // Note! This is a no-op for H2 (see QueueSqlDao.sql.stg and https://github.com/killbill/killbill/issues/223)
                transactional.resetLastInsertId();
                transactional.insertEntry(entry, config.getTableName());

                final Long lastInsertId = transactional.getLastInsertId();
                log.debug("{} Inserting entry: lastInsertId={}, entry={}", DB_QUEUE_LOG_ID, lastInsertId, entry);

                return lastInsertId;
            }
        });
    }

    public void reapEntries(final Date reapingDate) {
        executeTransaction(new Transaction<Void, QueueSqlDao<T>>() {
            @Override
            public Void inTransaction(final QueueSqlDao<T> transactional, final TransactionStatus status) throws Exception {
                final DateTime now = clock.getUTCNow();
                final List<T> entriesLeftBehind = transactional.getEntriesLeftBehind(config.getMaxReDispatchCount(), now.toDate(), reapingDate, config.getTableName());

                if (entriesLeftBehind.size() > 0) {
                    final Iterable<T> entriesToMove = Iterables.transform(entriesLeftBehind, new Function<T, T>() {
                        @Nullable
                        @Override
                        public T apply(@Nullable final T entry) {
                            entry.setProcessingState(PersistentQueueEntryLifecycleState.REAPED);
                            return entry;
                        }
                    });

                    moveEntriesToHistoryFromTransaction(transactional, entriesToMove);
                    insertReapedEntriesFromTransaction(transactional, entriesLeftBehind, now);

                    log.warn("{} {} entries were reaped by {}", DB_QUEUE_LOG_ID, entriesLeftBehind.size(), CreatorName.get());
                }

                return null;
            }
        });
    }

    private void insertReapedEntriesFromTransaction(final QueueSqlDao<T> transactional, final List<T> entriesLeftBehind, final DateTime now) {
        for (final T entry : entriesLeftBehind) {
            entry.setCreatedDate(now);
            entry.setProcessingState(PersistentQueueEntryLifecycleState.AVAILABLE);
            entry.setCreatingOwner(CreatorName.get());

            if (config.getPersistentQueueMode() == PersistentQueueMode.STICKY_EVENTS) {
                insertEntryFromTransaction(transactional, entry);
            } else {
                totalInsert.inc();
            }
        }

        if (config.getPersistentQueueMode() == PersistentQueueMode.STICKY_POLLING) {
            transactional.insertEntries(entriesLeftBehind, config.getTableName());
        }
    }

    private <U> U executeQuery(final Query<U, QueueSqlDao<T>> query) {
        return dbi.withHandle(new HandleCallback<U>() {
            @Override
            public U withHandle(final Handle handle) throws Exception {
                final U result = query.execute(handle.attach(sqlDaoClass));
                printSQLWarnings(handle);
                return result;
            }
        });
    }

    private <U> U executeTransaction(final Transaction<U, QueueSqlDao<T>> transaction) {
        return dbi.inTransaction(new TransactionCallback<U>() {
            @Override
            public U inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final U result = transaction.inTransaction(handle.attach(sqlDaoClass), status);
                printSQLWarnings(handle);
                return result;
            }
        });
    }

    private void printSQLWarnings(final Handle handle) {
        if (log.isDebugEnabled()) {
            try {
                SQLWarning warning = handle.getConnection().getWarnings();
                while (warning != null) {
                    log.debug("[SQL WARNING] {}", warning);
                    warning = warning.getNextWarning();
                }
                handle.getConnection().clearWarnings();
            } catch (final SQLException e) {
                log.debug("Error whilst retrieving SQL warnings", e);
            }
        }
    }

    private interface Query<U, QueueSqlDao> {

        U execute(QueueSqlDao sqlDao);
    }
}
