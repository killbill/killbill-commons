/*
 * Copyright 2010-2011 Ning, Inc.
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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
import java.util.Iterator;
import java.util.UUID;

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
import org.killbill.queue.dispatching.CallableCallbackBase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;

public class DefaultNotificationQueue implements NotificationQueue {

    private final DBBackedQueue<NotificationEventModelDao> dao;
    private final String svcName;
    private final String queueName;
    private final NotificationQueueHandler handler;
    private final NotificationQueueService notificationQueueService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final NotificationQueueConfig config;
    private final Profiling<Iterable<NotificationEventModelDao>, RuntimeException> prof;

    private volatile boolean isStarted;

    public DefaultNotificationQueue(final String svcName, final String queueName, final NotificationQueueHandler handler,
                                    final DBBackedQueue<NotificationEventModelDao> dao, final NotificationQueueService notificationQueueService,
                                    final Clock clock, final NotificationQueueConfig config) {
        this(svcName, queueName, handler, dao, notificationQueueService, clock, config, QueueObjectMapper.get());
    }

    public DefaultNotificationQueue(final String svcName, final String queueName, final NotificationQueueHandler handler,
                                    final DBBackedQueue<NotificationEventModelDao> dao, final NotificationQueueService notificationQueueService,
                                    final Clock clock, final NotificationQueueConfig config, final ObjectMapper objectMapper) {
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
        final Long searchKey2WithNull = MoreObjects.firstNonNull(searchKey2, new Long(0));
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
            public Void withSqlDao(final NotificationSqlDao transactional) throws Exception {
                dao.insertEntryFromTransaction(transactional, notification);
                return null;
            }
        };
        InTransaction.execute(connection, handler, NotificationSqlDao.class);
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
            public Void withSqlDao(final NotificationSqlDao transactional) throws Exception {
                ((NotificationSqlDao) dao.getSqlDao()).updateEntry(recordId, eventJson, searchKey1, searchKey2WithNull, config.getTableName());
                return null;
            }
        };
        InTransaction.execute(connection, handler, NotificationSqlDao.class);
    }



    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return getFutureNotificationsInternal((NotificationSqlDao) dao.getSqlDao(), null, searchKey1, searchKey2);
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<NotificationSqlDao, Iterable<NotificationEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, Iterable<NotificationEventWithMetadata<T>>>() {
            @Override
            public Iterable<NotificationEventWithMetadata<T>> withSqlDao(final NotificationSqlDao transactional) throws Exception {
                return getFutureNotificationsInternal(transactional, null, searchKey1, searchKey2);
            }
        };
        return InTransaction.execute(connection, handler, NotificationSqlDao.class);
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2) {
        return getFutureNotificationsInternal((NotificationSqlDao) dao.getSqlDao(), maxEffectiveDate, null, searchKey2);
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<NotificationSqlDao, Iterable<NotificationEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, Iterable<NotificationEventWithMetadata<T>>>() {
            @Override
            public Iterable<NotificationEventWithMetadata<T>> withSqlDao(final NotificationSqlDao transactional) throws Exception {
                return getFutureNotificationsInternal(transactional, maxEffectiveDate, null, searchKey2);
            }
        };
        return InTransaction.execute(connection, handler, NotificationSqlDao.class);
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
            public Iterable<NotificationEventWithMetadata<T>> withSqlDao(final NotificationSqlDao transactional) throws Exception {
                return getFutureOrInProcessingNotificationsInternal(transactional, null, searchKey1, searchKey2);
            }
        };
        return InTransaction.execute(connection, handler, NotificationSqlDao.class);
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2) {
        return getFutureOrInProcessingNotificationsInternal((NotificationSqlDao) dao.getSqlDao(), maxEffectiveDate, null, searchKey2);
    }

    @Override
    public <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationFromTransactionForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<NotificationSqlDao, Iterable<NotificationEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, Iterable<NotificationEventWithMetadata<T>>>() {
            @Override
            public Iterable<NotificationEventWithMetadata<T>> withSqlDao(final NotificationSqlDao transactional) throws Exception {
                return getFutureOrInProcessingNotificationsInternal(transactional, maxEffectiveDate, null, searchKey2);
            }
        };
        return InTransaction.execute(connection, handler, NotificationSqlDao.class);
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
    public void removeNotification(final Long recordId) {
        final NotificationEventModelDao existing = dao.getSqlDao().getByRecordId(recordId, config.getTableName());
        final NotificationEventModelDao removedEntry = new NotificationEventModelDao(existing, CreatorName.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.REMOVED);
        dao.moveEntryToHistory(removedEntry);
    }

    @Override
    public void removeNotificationFromTransaction(final Connection connection, final Long recordId) {
        final InTransaction.InTransactionHandler<NotificationSqlDao, Void> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, Void>() {
            @Override
            public Void withSqlDao(final NotificationSqlDao transactional) throws Exception {
                final NotificationEventModelDao existing = transactional.getByRecordId(recordId, config.getTableName());
                final NotificationEventModelDao removedEntry = new NotificationEventModelDao(existing, CreatorName.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.REMOVED);
                dao.moveEntryToHistoryFromTransaction(transactional, removedEntry);

                return null;
            }
        };
        InTransaction.execute(connection, handler, NotificationSqlDao.class);
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
    public boolean startQueue() {
        if (config.isProcessingOff()) {
            return false;
        }
        notificationQueueService.startQueue();
        isStarted = true;
        return true;
    }

    @Override
    public void stopQueue() {
        // Order matters...
        isStarted = false;
        notificationQueueService.stopQueue();
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }
}
