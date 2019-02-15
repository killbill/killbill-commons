/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.bus.dao.PersistentBusSqlDao;
import org.killbill.clock.Clock;
import org.killbill.commons.jdbi.notification.DatabaseTransactionEvent;
import org.killbill.commons.jdbi.notification.DatabaseTransactionEventType;
import org.killbill.commons.jdbi.notification.DatabaseTransactionNotificationApi;
import org.killbill.queue.api.PersistentQueueConfig;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.dao.EventEntryModelDao;
import org.killbill.queue.dao.QueueSqlDao;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

public class DBBackedQueueWithInflightQueue<T extends EventEntryModelDao> extends DBBackedQueue<T> {

    private static final Logger log = LoggerFactory.getLogger(DBBackedQueueWithInflightQueue.class);

    // How many recordIds we pull per iteration during init to fill the inflightQ
    private static final int MAX_FETCHED_RECORDS_ID = 1000;

    // Drain inflightQ using getMaxEntriesClaimed() config at a time and sleep for a maximum of 100 mSec if there is nothing to do
    private static final long INFLIGHT_POLLING_TIMEOUT_MSEC = 100;

    private final LinkedBlockingQueue<Long> inflightEvents;

    private final DatabaseTransactionNotificationApi databaseTransactionNotificationApi;

    //
    // Per thread information to keep track or recordId while it is accessible and right before
    // transaction gets committed/rollback
    //
    private static final AtomicInteger QUEUE_ID_CNT = new AtomicInteger(0);
    private final int queueId;
    private final TransientInflightQRowIdCache transientInflightQRowIdCache;

    public DBBackedQueueWithInflightQueue(final Clock clock,
                                          final IDBI dbi,
                                          final Class<? extends QueueSqlDao<T>> sqlDaoClass,
                                          final PersistentQueueConfig config,
                                          final String dbBackedQId,
                                          final MetricRegistry metricRegistry,
                                          final DatabaseTransactionNotificationApi databaseTransactionNotificationApi) {
        super(clock, dbi, sqlDaoClass, config, dbBackedQId, metricRegistry);
        this.queueId = QUEUE_ID_CNT.incrementAndGet();
        // We use an unboundedQ - the risk of running OUtOfMemory exists for a very large number of entries showing a more systematic problem...
        this.inflightEvents = new LinkedBlockingQueue<Long>();

        this.databaseTransactionNotificationApi = databaseTransactionNotificationApi;
        databaseTransactionNotificationApi.registerForNotification(this);

        // Metrics the size of the inflightQ
        metricRegistry.register(MetricRegistry.name(DBBackedQueueWithInflightQueue.class, dbBackedQId, "inflightQ", "size"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return inflightEvents.size();
            }
        });

        this.transientInflightQRowIdCache = new TransientInflightQRowIdCache(queueId);
    }

    @Override
    public void initialize() {

        initializeInflightQueue();
        log.info("{} Initialized with queueId={}, mode={}",
                 DB_QUEUE_LOG_ID, queueId, config.getPersistentQueueMode());
    }

    @Override
    public void close() {
        databaseTransactionNotificationApi.unregisterForNotification(this);
    }


    @Override
    public void insertEntryFromTransaction(final QueueSqlDao<T> transactional, final T entry) {
        final Long lastInsertId = safeInsertEntry(transactional, entry);
        if (lastInsertId == 0) {
            log.warn("{} Failed to insert entry, lastInsertedId={}", DB_QUEUE_LOG_ID, lastInsertId);
            return;
        }

        // The current thread is in the middle of  a transaction and this is the only times it knows about the recordId for the queue event;
        // It keeps track of it as a per thread data. Very soon, when the transaction gets committed/rolled back it can then extract the info
        // and insert the recordId into a blockingQ that is highly optimized to dispatch events.
        transientInflightQRowIdCache.addRowId(lastInsertId);
    }

    @Override
    public List<T> getReadyEntries() {
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
                log.warn("{} Got interrupted", DB_QUEUE_LOG_ID);
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

    @Override
    public void updateOnError(final T entry) {
        executeTransaction(new Transaction<Void, QueueSqlDao<T>>() {
            @Override
            public Void inTransaction(final QueueSqlDao<T> transactional, final TransactionStatus status) throws Exception {
                transactional.updateOnError(entry.getRecordId(), clock.getUTCNow().toDate(), entry.getErrorCount(), config.getTableName());
                transientInflightQRowIdCache.addRowId(entry.getRecordId());
                return null;
            }
        });
    }

    @Override
    protected void insertReapedEntriesFromTransaction(final QueueSqlDao<T> transactional, final List<T> entriesLeftBehind, final DateTime now) {
        for (final T entry : entriesLeftBehind) {
            entry.setCreatedDate(now);
            entry.setProcessingState(PersistentQueueEntryLifecycleState.AVAILABLE);
            entry.setCreatingOwner(CreatorName.get());
            insertEntryFromTransaction(transactional, entry);
        }
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
                    // Q overflowed, which means we will stop writing entries into the Q, and as a result, we will end up stop reading
                    // from the Q and return to polling mode
                } else {
                    log.warn("{} Inflight Q overflowed....", DB_QUEUE_LOG_ID, entry);
                }
            }
        } finally {
            transientInflightQRowIdCache.reset();
        }
    }

    @VisibleForTesting
    public int getInflightQSize() {
        return inflightEvents.size();
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

    private void initializeInflightQueue() {

        inflightEvents.clear();

        int totalEntries = 0;
        long fromRecordId = -1;
        do {
            final List<Long> existingIds = ((PersistentBusSqlDao) sqlDao).getReadyEntryIds(clock.getUTCNow().toDate(), fromRecordId, MAX_FETCHED_RECORDS_ID, CreatorName.get(), config.getTableName());
            if (existingIds.isEmpty()) {
                break;
            }

            inflightEvents.addAll(existingIds);
            totalEntries += existingIds.size();
            if (existingIds.size() < MAX_FETCHED_RECORDS_ID) {
                break;
            }
            fromRecordId = existingIds.get(existingIds.size() - 1) + 1;
        } while (true);

        log.info("{} Inserting {} entries into inflightQ during initialization",
                 DB_QUEUE_LOG_ID, totalEntries);

    }

}
