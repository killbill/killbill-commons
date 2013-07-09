package com.ning.billing.queue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.Hostname;
import com.ning.billing.queue.api.PersistentQueueConfig;
import com.ning.billing.queue.api.PersistentQueueEntryLifecycleState;
import com.ning.billing.queue.dao.EventEntryModelDao;
import com.ning.billing.queue.dao.QueueSqlDao;
import com.ning.billing.util.clock.Clock;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.sun.istack.internal.Nullable;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Counter;

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
    // Exponential backup retry logic, where we retyr up to 10 times for a maxium of about 10 sec ((521 + 256 + 128 + ... ) * 10)
    //
    private final static long INFLIGHT_ENTRIES_INITIAL_POLL_SLEEP_MS = 10;
    private final static int INFLIGHT_ENTRIES_POLL_MAX_RETRY = 10;


    private final String DB_QUEUE_LOG_ID;

    private final AtomicBoolean isQueueOpenForWrite;
    private final AtomicBoolean isQueueOpenForRead;
    private final QueueSqlDao<T> sqlDao;
    private final Clock clock;
    private final Queue<Long> inflightEvents;
    private final PersistentQueueConfig config;
    private final String tableName;
    private final String historyTableName;
    private final boolean useInflightQueue;

    private final Counter totalInflightProcessed;
    private final Counter totalProcessed;
    private final Counter totalInflightWritten;
    private final Counter totalWritten;

    public DBBackedQueue(final Clock clock,
                         final QueueSqlDao<T> sqlDao,
                         final PersistentQueueConfig config,
                         final String tableName,
                         final String historyTableName,
                         final String dbBackedQId,
                         final boolean useInflightQueue) {
        this.useInflightQueue = useInflightQueue;
        this.sqlDao = sqlDao;
        this.config = config;
        this.tableName = tableName;
        this.historyTableName = historyTableName;
        this.inflightEvents = useInflightQueue ? new LinkedBlockingQueue<Long>(config.getQueueCapacity()) : null;
        this.isQueueOpenForWrite = new AtomicBoolean(false);
        this.isQueueOpenForRead = new AtomicBoolean(false);
        this.clock = clock;
        this.totalInflightProcessed = Metrics.newCounter(DBBackedQueue.class, dbBackedQId + "-totalInflightProcessed");
        this.totalProcessed = Metrics.newCounter(DBBackedQueue.class, dbBackedQId + "-totalProcessed");
        this.totalInflightWritten = Metrics.newCounter(DBBackedQueue.class, dbBackedQId + "-totalInflightWritten");
        this.totalWritten = Metrics.newCounter(DBBackedQueue.class, dbBackedQId + "-totalWritten");

        DB_QUEUE_LOG_ID = "DBBackedQueue-" + dbBackedQId + ": ";
    }


    public void initialize() {
        final List<T> entries = fetchReadyEntries(config.getPrefetchEntries());
        if (entries.size() == 0) {
            isQueueOpenForWrite.set(true);
            isQueueOpenForRead.set(true);
        } else if (entries.size() < config.getPrefetchEntries()) {
            isQueueOpenForWrite.set(true);
            isQueueOpenForRead.set(false);
        } else {
            isQueueOpenForWrite.set(false);
            isQueueOpenForRead.set(false);
        }
        inflightEvents.clear();
        totalInflightProcessed.clear();
        totalProcessed.clear();
        totalInflightWritten.clear();
        totalWritten.clear();

        log.info(DB_QUEUE_LOG_ID + "Initialized with isQueueOpenForWrite = " + isQueueOpenForWrite.get() + ", isQueueOpenForRead" + isQueueOpenForRead.get());
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

        transactional.insertEntry(entry, tableName);
        totalWritten.inc();
        if (useInflightQueue && isQueueOpenForWrite.get()) {
            Long lastInsertId = transactional.getLastInsertId();

            boolean success = inflightEvents.offer(lastInsertId);

            log.debug(DB_QUEUE_LOG_ID + "Inserting entry " + lastInsertId +
                     (success ? "into inflightQ" : "into disk"));

            // Q overflowed
            if (!success) {
                final boolean q = isQueueOpenForWrite.compareAndSet(true, false);
                if (q) {
                    log.info(DB_QUEUE_LOG_ID + "Closing Q for write: Overflowed with recordId = " + lastInsertId);
                }
            } else {
                totalInflightWritten.inc();
            }
        }
    }


    public List<T> getReadyEntries() {

        List<T> candidates = ImmutableList.<T>of();

        // If we are not configured to use inflightQ then run expensive query
        if (!useInflightQueue) {
            final List<T> entriesToClaim = fetchReadyEntries(config.getMaxEntriesClaimed());
            if (entriesToClaim.size() > 0) {
                candidates = claimEntries(entriesToClaim);
            }
            return candidates;
        }

        if (isQueueOpenForRead.get()) {
            candidates = fetchReadyEntriesFromIds();

            // There are entries in the Q, we just return those
            if (candidates.size() > 0) {
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
            List<T> prefetchedEntries = fetchReadyEntries(config.getPrefetchEntries());
            // There is a small number so we re-enable adding entries in the Q
            if (prefetchedEntries.size() < config.getPrefetchEntries()) {
                log.info(DB_QUEUE_LOG_ID + " Opening Q for write");
                isQueueOpenForWrite.compareAndSet(false, true);
            }

            // Only keep as many candidates as we are allowed to
            final int candidateSize = prefetchedEntries.size() > config.getMaxEntriesClaimed() ? config.getMaxEntriesClaimed() : prefetchedEntries.size();
            candidates = prefetchedEntries.subList(0, candidateSize);
            totalProcessed.inc(candidates.size());
            //
            // If we see that we catch up with entries in the inflightQ, we need to switch mode and remove entries we are processing
            // Failure to remove the entries  would NOT trigger a bug, but might waste cycles where getReadyEntries() would return less
            // elements as expected, because entries have already been processed.
            //
            if (removeInflightEventsWhenSwitchingToQueueOpenForRead(candidates)) {
                log.info(DB_QUEUE_LOG_ID + " Opening Q for read");
                final boolean q = isQueueOpenForRead.compareAndSet(false, true);
                if (q) {
                    log.info(DB_QUEUE_LOG_ID + " Opening Q for read");
                }
            }
            return claimEntries(candidates);
        }
        return candidates;
    }


    private boolean removeInflightEventsWhenSwitchingToQueueOpenForRead(final List<T> candidates) {

        boolean foundEntryInInflightEvents = false;
        for (T entry : candidates) {
            foundEntryInInflightEvents = inflightEvents.remove(entry.getRecordId());
        }
        return foundEntryInInflightEvents;
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
        transactional.insertEntry(entry, historyTableName);
        transactional.removeEntry(entry.getRecordId(), tableName);
    }

    private List<T> fetchReadyEntriesFromIds() {
        final int size = config.getMaxEntriesClaimed() < inflightEvents.size() ? config.getMaxEntriesClaimed() : inflightEvents.size();
        final List<Long> recordIds = new ArrayList<Long>(size);
        for (int i = 0; i < size; i++) {
            final Long entryId = inflightEvents.poll();
            if (entryId != null) {
                totalInflightProcessed.inc();
                totalProcessed.inc();
                recordIds.add(entryId);
            }
        }

        log.debug(DB_QUEUE_LOG_ID + "fetchReadyEntriesFromIds, size = " + size + ", ids = " + Joiner.on(", ").join(recordIds));
        // Before we return we filter on AVAILABLE entries for precaution; the case could potentially happen
        // at the time when we switch from !isQueueOpenForRead -> isQueueOpenForRead with two thread in parallel.
        //
        List<T> result = ImmutableList.<T>of();
        if (recordIds.size() > 0) {
            final List<T> entriesFromIds = getEntriesFromIds(recordIds);
            result = ImmutableList.<T>copyOf(Collections2.filter(entriesFromIds, new Predicate<T>() {
                @Override
                public boolean apply(@Nullable final T input) {
                    return (input.getProcessingState() == PersistentQueueEntryLifecycleState.AVAILABLE);
                }
            }));
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
    //  The code below looks for all entries by retrying the lookup on disk, and if eventually returns the one that have been found.
    //  Note that:
    //  - It is is OK for that thread to sleep and retry as this is its nature -- it sleeps and polls
    //  - If for some reason the entry is not found but the transaction eventually commits, we will end up in a situation
    //    where we have entries AVALAIBLE on disk; those would be cleared as we restart the service. If this ends up being an issue
    //    we could had some additional logics to catch them.
    //
    private List<T> getEntriesFromIds(final List<Long> recordIds) {

        int originalSize = recordIds.size();
        List<T> result = new ArrayList<T>(recordIds.size());
        int nbTries = 0;

        do {
            final List<T> tmp = sqlDao.getEntriesFromIds(recordIds, tableName);
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
        final List<T> entries = sqlDao.getReadyEntries(now, Hostname.get(), size, tableName);
        return entries;
    }


    private List<T> claimEntries(List<T> candidates) {
        return ImmutableList.<T>copyOf(Collections2.filter(candidates, new Predicate<T>() {
            @Override
            public boolean apply(@Nullable final T input) {
                return claimEntry(input);
            }
        }));
    }

    private boolean claimEntry(T entry) {
        final Date nextAvailable = clock.getUTCNow().plus(config.getClaimedTime().getMillis()).toDate();
        final boolean claimed = (sqlDao.claimEntry(entry.getRecordId(), clock.getUTCNow().toDate(), Hostname.get(), nextAvailable, tableName) == 1);

        if (claimed) {
            log.debug(DB_QUEUE_LOG_ID + "Claiming entry " + entry.getRecordId());
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

    public long getTotalInflightProcessed() {
        return totalInflightProcessed.count();
    }

    public long getTotalProcessed() {
        return totalProcessed.count();
    }

    public long getTotalInflightWritten() {
        return totalInflightWritten.count();
    }

    public long getTotalWritten() {
        return totalWritten.count();
    }
}
