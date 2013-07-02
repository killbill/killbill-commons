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
import com.ning.billing.queue.api.EventEntry;
import com.ning.billing.queue.api.PersistentQueueConfig;
import com.ning.billing.queue.dao.QueueSqlDao;
import com.ning.billing.util.clock.Clock;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.sun.istack.internal.Nullable;

public class DBBackedQueue<T extends EventEntry> {

    // TODO STEPH config
    private final static int QUEUE_CAPACITY = 3000;
    private final static int PREFETCH_ENTRIES = 100;

    //private static final long DELTA_IN_PROCESSING_TIME_MS = 1000L * 60L * 5L; // 5 minutes

    private static final Logger log = LoggerFactory.getLogger(DBBackedQueue.class);

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


    public DBBackedQueue(final Clock clock,
                         final QueueSqlDao<T> sqlDao,
                         final PersistentQueueConfig config,
                         final String tableName,
                         final String historyTableName,
                         final String dbBackedQId,
                         final boolean useInflightQueue) {
        this.sqlDao = sqlDao;
        this.config = config;
        this.tableName = tableName;
        this.historyTableName = historyTableName;
        this.inflightEvents = new LinkedBlockingQueue<Long>(QUEUE_CAPACITY);
        this.isQueueOpenForWrite = new AtomicBoolean(false);
        this.isQueueOpenForRead  = new AtomicBoolean(false);
        this.clock = clock;
        this.useInflightQueue = useInflightQueue;
        DB_QUEUE_LOG_ID = "DBBackedQueue-" + dbBackedQId + ": ";
    }


    public void initialize() {
        final List<T> entries = fetchReadyEntries(PREFETCH_ENTRIES);
        if (entries.size() == 0) {
            isQueueOpenForWrite.set(true);
            isQueueOpenForRead.set(true);
        } else if  (entries.size() < PREFETCH_ENTRIES) {
            isQueueOpenForWrite.set(true);
            isQueueOpenForRead.set(false);
        } else {
            isQueueOpenForWrite.set(false);
            isQueueOpenForRead.set(false);
        }
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
        if (useInflightQueue && isQueueOpenForWrite.get()) {
            Long lastInsertId = transactional.getLastInsertId();
            boolean success = inflightEvents.add(lastInsertId);
            // Q overflowed
            if (!success) {
                log.info(DB_QUEUE_LOG_ID + " Overflowed with recordId = " + lastInsertId);
                isQueueOpenForWrite.set(false);
            }
        }
    }


    public List<T> getReadyEntries() {

        List<T> candidates = ImmutableList.<T>of();

        // If we are not configure to use inflightQ then run expensive query
        if (!useInflightQueue) {
            final List<T>  prefetchedEntries = fetchReadyEntries(config.getMaxEntriesClaimed());
            if (prefetchedEntries.size() > 0) {
                candidates = claimEntries(prefetchedEntries);
            }
            return candidates;
        }

        if (isQueueOpenForRead.get()) {
            candidates =  fetchReadyEntriesFromIds();

            // There are entries in the Q, we just return those
            if (candidates.size() > 0) {
                return claimEntries(candidates);
            }

            // There are no more entries in the Q but the Q is not open for write so either there is nothing to be read, or
            // the Q overflowed previously so we disable reading from the Q and continue below.
            if (!isQueueOpenForWrite.get()) {
                log.info(DB_QUEUE_LOG_ID + " Closing Q for read");
                isQueueOpenForRead.set(false);
            }
        }

        if (!isQueueOpenForRead.get()) {
            List<T>  prefetchedEntries = fetchReadyEntries(PREFETCH_ENTRIES);
            // There is a small number so we re-enable adding entries in the Q
            if (prefetchedEntries.size() < PREFETCH_ENTRIES) {
                log.info(DB_QUEUE_LOG_ID + " Opening Q for write");
                isQueueOpenForWrite.set(true);
            }

            // Only keep as many candidates as we are allowed to
            final int candidateSize = prefetchedEntries.size() >  config.getMaxEntriesClaimed() ? config.getMaxEntriesClaimed() : prefetchedEntries.size();
            candidates = prefetchedEntries.subList(0, candidateSize);

            // We should re-allow reading from Q if we start to see entries coming back that are in the Q.
            final Long inflightHead = inflightEvents.peek();
            if (inflightHead != null &&
                    Collections2.transform(candidates, new Function<T, Long>() {
                        @Override
                        public Long apply(@Nullable final T input) {
                            return input.getRecordId();
                        }
                    }).contains(inflightHead)) {
                log.info(DB_QUEUE_LOG_ID + " Opening Q for read");
                isQueueOpenForRead.set(true);
            }
            return claimEntries(candidates);
        }
        return candidates;
    }


    public void markEntryAsProcessed(final T entry) {
        sqlDao.inTransaction(new Transaction<Void, QueueSqlDao<T>>() {
            @Override
            public Void inTransaction(final QueueSqlDao<T> transactional, final TransactionStatus status) throws Exception {
                transactional.insertEntry(entry, historyTableName);
                transactional.removeEntry(entry.getRecordId(), tableName);
                return null;
            }
        });
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
        return (recordIds.size() > 0) ? sqlDao.getReadyEntriesFromIds(recordIds, tableName) : ImmutableList.<T>of();
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
        return claimed;
    }


}
