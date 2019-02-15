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
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.clock.Clock;
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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

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
public abstract class DBBackedQueue<T extends EventEntryModelDao> {

    protected static final Logger log = LoggerFactory.getLogger(DBBackedQueue.class);

    protected final String DB_QUEUE_LOG_ID;

    protected final IDBI dbi;
    protected final Class<? extends QueueSqlDao<T>> sqlDaoClass;
    protected final QueueSqlDao<T> sqlDao;
    protected final Clock clock;
    protected final PersistentQueueConfig config;

    //
    // All these *raw* time measurement only measure the query time *not* including the transaction and the time to acquire DB connection
    //
    // Time to get the ready entries (polling) or all entries from ids (inflight)
    protected final Timer rawGetEntriesTime;
    // Time to insert one entry in the DB
    protected final Timer rawInsertEntryTime;
    // Time to claim the batch of entries (STICKY_POLLING)
    protected final Timer rawClaimEntriesTime;
    // Time to claim one entry (POLLING mode)
    protected final Timer rawClaimEntryTime;
    // Time to move a batch of entries (delete from table + insert into history)
    protected final Timer rawDeleteEntriesTime;
    // Time to move one entry (delete from table + insert into history)
    protected final Timer rawDeleteEntryTime;

    protected final Profiling<Long, RuntimeException> prof;

    public DBBackedQueue(final Clock clock,
                         final IDBI dbi,
                         final Class<? extends QueueSqlDao<T>> sqlDaoClass,
                         final PersistentQueueConfig config,
                         final String dbBackedQId,
                         final MetricRegistry metricRegistry) {
        this.dbi = dbi;
        this.sqlDaoClass = sqlDaoClass;
        this.sqlDao = dbi.onDemand(sqlDaoClass);
        this.config = config;
        this.clock = clock;
        this.prof = new Profiling<Long, RuntimeException>();

        this.rawGetEntriesTime = metricRegistry.timer(MetricRegistry.name(DBBackedQueue.class, "rawGetEntriesTime"));
        this.rawInsertEntryTime = metricRegistry.timer(MetricRegistry.name(DBBackedQueue.class, "rawInsertEntryTime"));
        this.rawClaimEntriesTime = metricRegistry.timer(MetricRegistry.name(DBBackedQueue.class, "rawClaimEntriesTime"));
        this.rawClaimEntryTime = metricRegistry.timer(MetricRegistry.name(DBBackedQueue.class, "rawClaimEntryTime"));
        this.rawDeleteEntriesTime = metricRegistry.timer(MetricRegistry.name(DBBackedQueue.class, "rawDeleteEntriesTime"));
        this.rawDeleteEntryTime = metricRegistry.timer(MetricRegistry.name(DBBackedQueue.class, "rawDeleteEntryTime"));

        this.DB_QUEUE_LOG_ID = "DBBackedQueue-" + dbBackedQId;
    }

    public static class ReadyEntriesWithMetrics<T extends EventEntryModelDao> {
        private final List<T> entries;
        private final long time;

        public ReadyEntriesWithMetrics(final List<T> entries, final long time) {
            this.entries = entries;
            this.time = time;
        }

        public List<T> getEntries() {
            return entries;
        }

        public long getTime() {
            return time;
        }
    }

    public abstract void initialize();

    public abstract void close();

    public abstract ReadyEntriesWithMetrics<T> getReadyEntries();

    public abstract void insertEntryFromTransaction(final QueueSqlDao<T> transactional, final T entry);

    public abstract void updateOnError(final T entry);

    protected abstract void insertReapedEntriesFromTransaction(final QueueSqlDao<T> transactional, final List<T> entriesLeftBehind, final DateTime now);

    public void insertEntry(final T entry) {
        executeTransaction(new Transaction<Void, QueueSqlDao<T>>() {
            @Override
            public Void inTransaction(final QueueSqlDao<T> transactional, final TransactionStatus status) {
                insertEntryFromTransaction(transactional, entry);
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
                case PROCESSED:
                case REMOVED:
                case REAPED:
                    break;
                default:
                    log.warn("{} Unexpected terminal event state={} for record_id={}", DB_QUEUE_LOG_ID, entry.getProcessingState(), entry.getRecordId());
                    break;
            }

            log.debug("{} Moving entry into history: recordId={}, className={}, json={}", DB_QUEUE_LOG_ID, entry.getRecordId(), entry.getClassName(), entry.getEventJson());

            long ini = System.nanoTime();
            transactional.insertEntry(entry, config.getHistoryTableName());
            transactional.removeEntry(entry.getRecordId(), config.getTableName());
            rawDeleteEntryTime.update(System.nanoTime() - ini, TimeUnit.NANOSECONDS);

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

    public void moveEntriesToHistoryFromTransaction(final QueueSqlDao<T> transactional, final Iterable<T> entries) {
        if (!entries.iterator().hasNext()) {
            return;
        }

        for (final T cur : entries) {
            switch (cur.getProcessingState()) {
                case FAILED:
                case PROCESSED:
                case REMOVED:
                case REAPED:
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
        long ini = System.nanoTime();
        transactional.insertEntries(entries, config.getHistoryTableName());
        transactional.removeEntries(ImmutableList.<Long>copyOf(toBeRemovedRecordIds), config.getTableName());
        rawDeleteEntriesTime.update(System.nanoTime() - ini, TimeUnit.NANOSECONDS);
    }

    protected long getNbReadyEntries() {
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


    protected Long safeInsertEntry(final QueueSqlDao<T> transactional, final T entry) {
        return prof.executeWithProfiling(ProfilingFeature.ProfilingFeatureType.DAO, "QueueSqlDao:insert", new Profiling.WithProfilingCallback<Long, RuntimeException>() {

            @Override
            public Long execute() throws RuntimeException {

                long init = System.nanoTime();

                // LAST_INSERT_ID is kept at the transaction level; we reset it to 0 so that in case insert fails, we don't end up with a previous
                // value that would end up corrupting the inflightQ
                // Note! This is a no-op for H2 (see QueueSqlDao.sql.stg and https://github.com/killbill/killbill/issues/223)
                transactional.resetLastInsertId();
                transactional.insertEntry(entry, config.getTableName());

                final Long lastInsertId = transactional.getLastInsertId();
                log.debug("{} Inserting entry: lastInsertId={}, entry={}", DB_QUEUE_LOG_ID, lastInsertId, entry);

                rawInsertEntryTime.update(System.nanoTime() - init, TimeUnit.NANOSECONDS);
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

    protected <U> U executeQuery(final Query<U, QueueSqlDao<T>> query) {
        return dbi.withHandle(new HandleCallback<U>() {
            @Override
            public U withHandle(final Handle handle) throws Exception {
                final U result = query.execute(handle.attach(sqlDaoClass));
                printSQLWarnings(handle);
                return result;
            }
        });
    }

    protected <U> U executeTransaction(final Transaction<U, QueueSqlDao<T>> transaction) {
        return dbi.inTransaction(new TransactionCallback<U>() {
            @Override
            public U inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final U result = transaction.inTransaction(handle.attach(sqlDaoClass), status);
                printSQLWarnings(handle);
                return result;
            }
        });
    }

    protected void printSQLWarnings(final Handle handle) {
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

    protected interface Query<U, QueueSqlDao> {

        U execute(QueueSqlDao sqlDao);
    }

    public QueueSqlDao<T> getSqlDao() {
        return sqlDao;
    }

}
