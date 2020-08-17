/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.notificationq;

import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.clock.Clock;
import org.killbill.commons.profiling.Profiling;
import org.killbill.commons.profiling.ProfilingFeature;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueConfig;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.killbill.notificationq.dao.NotificationEventModelDao;
import org.killbill.notificationq.dao.NotificationSqlDao;
import org.killbill.queue.DBBackedQueue;
import org.killbill.queue.InTransaction;
import org.killbill.queue.QueueObjectMapper;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.dao.QueueSqlDao;
import org.killbill.queue.dispatching.CallableCallbackBase;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;

public class DefaultNotificationQueue implements NotificationQueue {

    private static final Logger logger = LoggerFactory.getLogger(DefaultNotificationQueue.class);

    private final DBI dbi;
    private final DBBackedQueue<NotificationEventModelDao> dao;
    private final String svcName;
    private final String queueName;
    private final NotificationQueueHandler handler;
    private final NotificationQueueService notificationQueueService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final NotificationQueueConfig config;
    private final Profiling<Iterable<NotificationEventModelDao>, RuntimeException> prof;

    private AtomicBoolean isInitialized;
    private AtomicBoolean isStarted;

    public DefaultNotificationQueue(final String svcName, final String queueName, final NotificationQueueHandler handler,
                                    final DBI dbi, final DBBackedQueue<NotificationEventModelDao> dao, final NotificationQueueService notificationQueueService,
                                    final Clock clock, final NotificationQueueConfig config) {
        this(svcName, queueName, handler, dbi, dao, notificationQueueService, clock, config, QueueObjectMapper.get());
    }

    public DefaultNotificationQueue(final String svcName, final String queueName, final NotificationQueueHandler handler,
                                    final DBI dbi, final DBBackedQueue<NotificationEventModelDao> dao, final NotificationQueueService notificationQueueService,
                                    final Clock clock, final NotificationQueueConfig config, final ObjectMapper objectMapper) {
        this.isStarted = new AtomicBoolean(false);
        this.isInitialized = new AtomicBoolean(false);
        this.dbi = dbi;
        this.svcName = svcName;
        this.queueName = queueName;
        this.handler = handler;
        this.dao = dao;
        this.notificationQueueService = notificationQueueService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.config = config;
        this.prof = new Profiling<Iterable<NotificationEventModelDao>, RuntimeException>();
    }

    @Override
    public void recordFutureNotification(final DateTime futureNotificationTime, final NotificationEvent event, final UUID userToken, final Long searchKey1, final Long searchKey2) throws IOException {
        final String eventJson = objectMapper.writeValueAsString(event);
        final UUID futureUserToken = UUID.randomUUID();
        final Long searchKey2WithNull = MoreObjects.firstNonNull(searchKey2, 0L);
        final NotificationEventModelDao notification = new NotificationEventModelDao(CreatorName.get(), clock.getUTCNow(), event.getClass().getName(), eventJson, userToken, searchKey1, searchKey2WithNull, futureUserToken, futureNotificationTime, getFullQName());
        dao.insertEntry(notification);
    }

    @Override
    public void recordFutureNotificationFromTransaction(final Connection connection, final DateTime futureNotificationTime, final NotificationEvent event,
                                                        final UUID userToken, final Long searchKey1, final Long searchKey2) throws IOException {
        final String eventJson = objectMapper.writeValueAsString(event);
        final UUID futureUserToken = UUID.randomUUID();
        final Long searchKey2WithNull = MoreObjects.firstNonNull(searchKey2, 0L);
        final NotificationEventModelDao notification = new NotificationEventModelDao(CreatorName.get(), clock.getUTCNow(), event.getClass().getName(), eventJson, userToken, searchKey1, searchKey2WithNull, futureUserToken, futureNotificationTime, getFullQName());

        final InTransaction.InTransactionHandler<NotificationSqlDao, Void> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, Void>() {
            @Override
            public Void withSqlDao(final NotificationSqlDao transactional) {
                dao.insertEntryFromTransaction(transactional, notification);
                return null;
            }
        };
        InTransaction.execute(dbi, connection, handler, NotificationSqlDao.class);
    }

    @Override
    public void updateFutureNotification(final Long recordId, final NotificationEvent event, final Long searchKey1, final Long searchKey2) throws IOException {
        final String eventJson = objectMapper.writeValueAsString(event);
        final Long searchKey2WithNull = MoreObjects.firstNonNull(searchKey2, 0L);
        ((NotificationSqlDao) dao.getSqlDao()).updateEntry(recordId, eventJson, searchKey1, searchKey2WithNull, config.getTableName());
    }

    @Override
    public void updateFutureNotificationFromTransaction(final Connection connection,
                                                 final Long recordId,
                                                 final NotificationEvent event,
                                                 final Long searchKey1,
                                                 final Long searchKey2) throws IOException {


        final String eventJson = objectMapper.writeValueAsString(event);
        final Long searchKey2WithNull = MoreObjects.firstNonNull(searchKey2, 0L);
        final InTransaction.InTransactionHandler<NotificationSqlDao, Void> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, Void>() {
            @Override
            public Void withSqlDao(final NotificationSqlDao transactional) {
                ((NotificationSqlDao) dao.getSqlDao()).updateEntry(recordId, eventJson, searchKey1, searchKey2WithNull, config.getTableName());
                return null;
            }
        };
        InTransaction.execute(dbi, connection, handler, NotificationSqlDao.class);
    }



    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return getFutureNotificationsInternal((NotificationSqlDao) dao.getSqlDao(), null, searchKey1, searchKey2);
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<NotificationSqlDao, Iterable<NotificationEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, Iterable<NotificationEventWithMetadata<T>>>() {
            @Override
            public Iterable<NotificationEventWithMetadata<T>> withSqlDao(final NotificationSqlDao transactional) {
                return getFutureNotificationsInternal(transactional, null, searchKey1, searchKey2);
            }
        };
        return InTransaction.execute(dbi, connection, handler, NotificationSqlDao.class);
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2) {
        return getFutureNotificationsInternal((NotificationSqlDao) dao.getSqlDao(), maxEffectiveDate, null, searchKey2);
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<NotificationSqlDao, Iterable<NotificationEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, Iterable<NotificationEventWithMetadata<T>>>() {
            @Override
            public Iterable<NotificationEventWithMetadata<T>> withSqlDao(final NotificationSqlDao transactional) {
                return getFutureNotificationsInternal(transactional, maxEffectiveDate, null, searchKey2);
            }
        };
        return InTransaction.execute(dbi, connection, handler, NotificationSqlDao.class);
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getInProcessingNotifications() {
        return toNotificationEventWithMetadata(dao.getSqlDao().getInProcessingEntries(config.getTableName()));
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return getFutureOrInProcessingNotificationsInternal((NotificationSqlDao) dao.getSqlDao(), null, searchKey1, searchKey2);
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<NotificationSqlDao, Iterable<NotificationEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, Iterable<NotificationEventWithMetadata<T>>>() {
            @Override
            public Iterable<NotificationEventWithMetadata<T>> withSqlDao(final NotificationSqlDao transactional) {
                return getFutureOrInProcessingNotificationsInternal(transactional, null, searchKey1, searchKey2);
            }
        };
        return InTransaction.execute(dbi, connection, handler, NotificationSqlDao.class);
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2) {
        return getFutureOrInProcessingNotificationsInternal((NotificationSqlDao) dao.getSqlDao(), maxEffectiveDate, null, searchKey2);
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationFromTransactionForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<NotificationSqlDao, Iterable<NotificationEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, Iterable<NotificationEventWithMetadata<T>>>() {
            @Override
            public Iterable<NotificationEventWithMetadata<T>> withSqlDao(final NotificationSqlDao transactional) {
                return getFutureOrInProcessingNotificationsInternal(transactional, maxEffectiveDate, null, searchKey2);
            }
        };
        return InTransaction.execute(dbi, connection, handler, NotificationSqlDao.class);
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getHistoricalNotificationForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return getHistoricalNotificationsInternal((NotificationSqlDao) dao.getSqlDao(), null, searchKey1, searchKey2);
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getHistoricalNotificationForSearchKey2(final DateTime minEffectiveDate, final Long searchKey2) {
        return getHistoricalNotificationsInternal((NotificationSqlDao) dao.getSqlDao(), minEffectiveDate, null, searchKey2);
    }

    private <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureNotificationsInternal(final NotificationSqlDao transactionalDao, @Nullable final DateTime maxEffectiveDate, @Nullable final Long searchKey1, final Long searchKey2) {
        final Iterable<NotificationEventModelDao> entries = getFutureNotificationsInternalWithProfiling(transactionalDao, maxEffectiveDate, searchKey1, searchKey2);
        return toNotificationEventWithMetadata(entries);
    }

    private <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationsInternal(final NotificationSqlDao transactionalDao, @Nullable final DateTime maxEffectiveDate, @Nullable final Long searchKey1, final Long searchKey2) {
        final Iterable<NotificationEventModelDao> entries = getFutureOrInProcessingNotificationsInternalWithProfiling(transactionalDao, maxEffectiveDate, searchKey1, searchKey2);
        return toNotificationEventWithMetadata(entries);
    }

    private <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getHistoricalNotificationsInternal(final NotificationSqlDao transactionalDao, @Nullable final DateTime minEffectiveDate, @Nullable final Long searchKey1, final Long searchKey2) {
        final Iterable<NotificationEventModelDao> entries = getHistoricalNotificationsInternalWithProfiling(transactionalDao, minEffectiveDate, searchKey1, searchKey2);
        return toNotificationEventWithMetadata(entries);
    }

    private Iterable<NotificationEventModelDao> getFutureNotificationsInternalWithProfiling(final NotificationSqlDao transactionalDao, @Nullable final DateTime maxEffectiveDate, @Nullable final Long searchKey1, final Long searchKey2) {
        return prof.executeWithProfiling(ProfilingFeature.ProfilingFeatureType.DAO, "DAO:NotificationSqlDao:getReadyQueueEntriesForSearchKeys", new Profiling.WithProfilingCallback<Iterable<NotificationEventModelDao>, RuntimeException>() {
            @Override
            public Iterable<NotificationEventModelDao> execute() throws RuntimeException {
                return new Iterable<NotificationEventModelDao>() {
                    @Override
                    public Iterator<NotificationEventModelDao> iterator() {
                        return searchKey1 != null ?
                               transactionalDao.getReadyQueueEntriesForSearchKeys(getFullQName(), searchKey1, searchKey2, config.getTableName()) :
                               transactionalDao.getReadyQueueEntriesForSearchKey2(getFullQName(), maxEffectiveDate, searchKey2, config.getTableName());
                    }
                };
            }
        });
    }

    private Iterable<NotificationEventModelDao> getFutureOrInProcessingNotificationsInternalWithProfiling(final NotificationSqlDao transactionalDao, @Nullable final DateTime maxEffectiveDate, @Nullable final Long searchKey1, final Long searchKey2) {
        return prof.executeWithProfiling(ProfilingFeature.ProfilingFeatureType.DAO, "DAO:NotificationSqlDao:getReadyOrInProcessingQueueEntriesForSearchKeys", new Profiling.WithProfilingCallback<Iterable<NotificationEventModelDao>, RuntimeException>() {
            @Override
            public Iterable<NotificationEventModelDao> execute() throws RuntimeException {
                return new Iterable<NotificationEventModelDao>() {
                    @Override
                    public Iterator<NotificationEventModelDao> iterator() {
                        return searchKey1 != null ?
                               transactionalDao.getReadyOrInProcessingQueueEntriesForSearchKeys(getFullQName(), searchKey1, searchKey2, config.getTableName()) :
                               transactionalDao.getReadyOrInProcessingQueueEntriesForSearchKey2(getFullQName(), maxEffectiveDate, searchKey2, config.getTableName());
                    }
                };
            }
        });
    }

    private Iterable<NotificationEventModelDao> getHistoricalNotificationsInternalWithProfiling(final NotificationSqlDao transactionalDao, @Nullable final DateTime minEffectiveDate, @Nullable final Long searchKey1, final Long searchKey2) {
        return prof.executeWithProfiling(ProfilingFeature.ProfilingFeatureType.DAO, "DAO:NotificationSqlDao:getHistoricalQueueEntriesForSearchKeys", new Profiling.WithProfilingCallback<Iterable<NotificationEventModelDao>, RuntimeException>() {
            @Override
            public Iterable<NotificationEventModelDao> execute() throws RuntimeException {
                return new Iterable<NotificationEventModelDao>() {
                    @Override
                    public Iterator<NotificationEventModelDao> iterator() {
                        return searchKey1 != null ?
                               transactionalDao.getHistoricalQueueEntriesForSearchKeys(getFullQName(), searchKey1, searchKey2, config.getHistoryTableName()) :
                               transactionalDao.getHistoricalQueueEntriesForSearchKey2(getFullQName(), minEffectiveDate, searchKey2, config.getHistoryTableName());
                    }
                };
            }
        });
    }

    private <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> toNotificationEventWithMetadata(final Iterable<NotificationEventModelDao> entries) {
        return Iterables.<NotificationEventModelDao, NotificationEventWithMetadata<T>>transform(entries,
                                                                                                new Function<NotificationEventModelDao, NotificationEventWithMetadata<T>>() {
                                                                                                    @Override
                                                                                                    public NotificationEventWithMetadata<T> apply(final NotificationEventModelDao input) {
                                                                                                        return toNotificationEventWithMetadata(input);
                                                                                                    }
                                                                                                });
    }

    private <T extends NotificationEvent> NotificationEventWithMetadata<T> toNotificationEventWithMetadata(final NotificationEventModelDao cur) {
        final T event = CallableCallbackBase.deserializeEvent(cur, objectMapper);
        return new NotificationEventWithMetadata<T>(cur.getRecordId(), cur.getUserToken(), cur.getCreatedDate(), cur.getSearchKey1(), cur.getSearchKey2(), event,
                                                    cur.getFutureUserToken(), cur.getEffectiveDate(), cur.getQueueName());
    }

    @Override
    public long getNbReadyEntries(final DateTime maxCreatedDate) {
        return dao.getNbReadyEntries(maxCreatedDate.toDate());
    }

    @Override
    public void removeNotification(final Long recordId) {
        final NotificationEventModelDao existing = dao.getSqlDao().getByRecordId(recordId, config.getTableName());
        final NotificationEventModelDao removedEntry = new NotificationEventModelDao(existing, CreatorName.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.REMOVED);
        dao.moveEntryToHistory(removedEntry);
    }

    @Override
    public void removeNotificationFromTransaction(final Connection connection, final Long recordId) {
        final InTransaction.InTransactionHandler<NotificationSqlDao, Void> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, Void>() {
            @Override
            public Void withSqlDao(final NotificationSqlDao transactional) {
                final NotificationEventModelDao existing = transactional.getByRecordId(recordId, config.getTableName());
                final NotificationEventModelDao removedEntry = new NotificationEventModelDao(existing, CreatorName.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.REMOVED);
                dao.moveEntryToHistoryFromTransaction(transactional, removedEntry);

                return null;
            }
        };
        InTransaction.execute(dbi, connection, handler, NotificationSqlDao.class);
    }

    @Override
    public void removeFutureNotificationsForSearchKeys(final Long searchKey1, final Long searchKey2) {
        dao.getSqlDao().inTransaction(new Transaction<Void, QueueSqlDao<NotificationEventModelDao>>() {
            @Override
            public Void inTransaction(final QueueSqlDao<NotificationEventModelDao> transactional, final TransactionStatus status) throws Exception {
                // Move entries by batch into the history table
                final int batchSize = 25;
                final Collection<NotificationEventModelDao> currentBatch = new ArrayList<NotificationEventModelDao>(batchSize);

                // Note that we don't claim them here, so it could be possible that some of these entries end up being processed nonetheless
                final Iterator<NotificationEventModelDao> futureQueueEntriesForSearchKeys = ((NotificationSqlDao) transactional).getReadyQueueEntriesForSearchKeys(getFullQName(),
                                                                                                                                                                   searchKey1,
                                                                                                                                                                   searchKey2,
                                                                                                                                                                   config.getTableName());
                try {
                    while (futureQueueEntriesForSearchKeys.hasNext()) {
                        final NotificationEventModelDao notificationEventModelDao = futureQueueEntriesForSearchKeys.next();
                        notificationEventModelDao.setProcessingState(PersistentQueueEntryLifecycleState.REMOVED);
                        currentBatch.add(notificationEventModelDao);

                        if (currentBatch.size() >= batchSize || !futureQueueEntriesForSearchKeys.hasNext()) {
                            dao.moveEntriesToHistoryFromTransaction(transactional, currentBatch);
                            currentBatch.clear();
                        }
                    }
                } finally {
                    // This will go through all results to close the connection
                    final int nbNotificationsLeft = Iterators.size(futureQueueEntriesForSearchKeys);
                    if (nbNotificationsLeft > 0) {
                        logger.warn("Unable to remove {} notifications for searchKey1={}, searchKey2={}", searchKey1, searchKey2);
                    }
                }

                return null;
            }
        });
    }

    @Override
    public String getFullQName() {
        return NotificationQueueServiceBase.getCompositeName(svcName, queueName);
    }

    @Override
    public String getServiceName() {
        return svcName;
    }

    @Override
    public String getQueueName() {
        return queueName;
    }

    @Override
    public NotificationQueueHandler getHandler() {
        return handler;
    }

    @Override
    public boolean initQueue() {
        if (config.isProcessingOff()) {
            return false;
        }

        if (!isInitialized.compareAndSet(false, true)) {
            return notificationQueueService.initQueue();
        } else {
            return false;
        }
    }

    @Override
    public boolean startQueue() {
        if (config.isProcessingOff()) {
            logger.warn("Not starting queue {} because of xxx.notification.off config", getFullQName());
            return false;
        }

        if (isStarted.compareAndSet(false, true)) {
            notificationQueueService.startQueue();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void stopQueue() {
        if (isStarted.compareAndSet(true, false)) {
            isInitialized.set(false);
            notificationQueueService.stopQueue();
            return;
        }
    }

    @Override
    public boolean isStarted() {
        return isStarted.get();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultNotificationQueue{");
        sb.append("svcName='").append(svcName).append('\'');
        sb.append(", queueName='").append(queueName).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
