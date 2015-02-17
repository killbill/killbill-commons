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
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.Hostname;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueConfig;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.killbill.notificationq.dao.NotificationEventModelDao;
import org.killbill.notificationq.dao.NotificationSqlDao;
import org.killbill.queue.DBBackedQueue;
import org.killbill.queue.DefaultQueueLifecycle;
import org.killbill.queue.InTransaction;
import org.killbill.queue.QueueObjectMapper;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Objects;

public class DefaultNotificationQueue implements NotificationQueue {

    private final DBBackedQueue<NotificationEventModelDao> dao;
    private final String svcName;
    private final String queueName;
    private final NotificationQueueHandler handler;
    private final NotificationQueueService notificationQueueService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final NotificationQueueConfig config;

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
    }

    @Override
    public void recordFutureNotification(final DateTime futureNotificationTime, final NotificationEvent event, final UUID userToken, final Long searchKey1, final Long searchKey2) throws IOException {
        final String eventJson = objectMapper.writeValueAsString(event);
        final UUID futureUserToken = UUID.randomUUID();
        final Long searchKey2WithNull = Objects.firstNonNull(searchKey2, new Long(0));
        final NotificationEventModelDao notification = new NotificationEventModelDao(Hostname.get(), clock.getUTCNow(), event.getClass().getName(), eventJson, userToken, searchKey1, searchKey2WithNull, futureUserToken, futureNotificationTime, getFullQName());
        dao.insertEntry(notification);
    }

    @Override
    public void recordFutureNotificationFromTransaction(final Connection connection, final DateTime futureNotificationTime, final NotificationEvent event,
                                                        final UUID userToken, final Long searchKey1, final Long searchKey2) throws IOException {
        final String eventJson = objectMapper.writeValueAsString(event);
        final UUID futureUserToken = UUID.randomUUID();
        final Long searchKey2WithNull = Objects.firstNonNull(searchKey2, 0L);
        final NotificationEventModelDao notification = new NotificationEventModelDao(Hostname.get(), clock.getUTCNow(), event.getClass().getName(), eventJson, userToken, searchKey1, searchKey2WithNull, futureUserToken, futureNotificationTime, getFullQName());

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
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return getFutureNotificationsInternal((NotificationSqlDao) dao.getSqlDao(), searchKey1, searchKey2);
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<NotificationSqlDao, List<NotificationEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, List<NotificationEventWithMetadata<T>>>() {
            @Override
            public List<NotificationEventWithMetadata<T>> withSqlDao(final NotificationSqlDao transactional) throws Exception {
                return getFutureNotificationsInternal(transactional, searchKey1, searchKey2);
            }
        };
        return InTransaction.execute(connection, handler, NotificationSqlDao.class);
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKey2(final Long searchKey2) {
        return getFutureNotificationsInternal((NotificationSqlDao) dao.getSqlDao(), null, searchKey2);
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKey2(final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<NotificationSqlDao, List<NotificationEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, List<NotificationEventWithMetadata<T>>>() {
            @Override
            public List<NotificationEventWithMetadata<T>> withSqlDao(final NotificationSqlDao transactional) throws Exception {
                return getFutureNotificationsInternal(transactional, null, searchKey2);
            }
        };
        return InTransaction.execute(connection, handler, NotificationSqlDao.class);
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getInProcessingNotifications() {
        return toNotificationEventWithMetadataList(dao.getSqlDao().getInProcessingEntries(config.getTableName()));
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return getFutureOrInProcessingNotificationsInternal((NotificationSqlDao) dao.getSqlDao(), searchKey1, searchKey2);
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<NotificationSqlDao, List<NotificationEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, List<NotificationEventWithMetadata<T>>>() {
            @Override
            public List<NotificationEventWithMetadata<T>> withSqlDao(final NotificationSqlDao transactional) throws Exception {
                return getFutureOrInProcessingNotificationsInternal(transactional, searchKey1, searchKey2);
            }
        };
        return InTransaction.execute(connection, handler, NotificationSqlDao.class);
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationForSearchKey2(final Long searchKey2) {
        return getFutureOrInProcessingNotificationsInternal((NotificationSqlDao) dao.getSqlDao(), null, searchKey2);
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationFromTransactionForSearchKey2(final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<NotificationSqlDao, List<NotificationEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, List<NotificationEventWithMetadata<T>>>() {
            @Override
            public List<NotificationEventWithMetadata<T>> withSqlDao(final NotificationSqlDao transactional) throws Exception {
                return getFutureOrInProcessingNotificationsInternal(transactional, null, searchKey2);
            }
        };
        return InTransaction.execute(connection, handler, NotificationSqlDao.class);
    }

    private <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationsInternal(final NotificationSqlDao transactionalDao, @Nullable final Long searchKey1, final Long searchKey2) {
        final List<NotificationEventModelDao> entries = searchKey1 != null ?
                                                        transactionalDao.getReadyQueueEntriesForSearchKeys(getFullQName(), searchKey1, searchKey2, config.getTableName()) :
                                                        transactionalDao.getReadyQueueEntriesForSearchKey2(getFullQName(), searchKey2, config.getTableName());
        return toNotificationEventWithMetadataList(entries);
    }

    private <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationsInternal(final NotificationSqlDao transactionalDao, @Nullable final Long searchKey1, final Long searchKey2) {
        final List<NotificationEventModelDao> entries = searchKey1 != null ?
                                                        transactionalDao.getReadyOrInProcessingQueueEntriesForSearchKeys(getFullQName(), searchKey1, searchKey2, config.getTableName()) :
                                                        transactionalDao.getReadyOrInProcessingQueueEntriesForSearchKey2(getFullQName(), searchKey2, config.getTableName());
        return toNotificationEventWithMetadataList(entries);
    }

    private <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> toNotificationEventWithMetadataList(final List<NotificationEventModelDao> entries) {
        final List<NotificationEventWithMetadata<T>> result = new LinkedList<NotificationEventWithMetadata<T>>();
        for (final NotificationEventModelDao cur : entries) {
            final T event = (T) DefaultQueueLifecycle.deserializeEvent(cur.getClassName(), objectMapper, cur.getEventJson());
            final NotificationEventWithMetadata<T> eventWithMetadata = new NotificationEventWithMetadata<T>(cur.getRecordId(), cur.getUserToken(), cur.getCreatedDate(), cur.getSearchKey1(), cur.getSearchKey2(), event,
                                                                                                            cur.getFutureUserToken(), cur.getEffectiveDate(), cur.getQueueName());
            result.add(eventWithMetadata);
        }
        return result;
    }

    @Override
    public void removeNotification(final Long recordId) {
        final NotificationEventModelDao existing = dao.getSqlDao().getByRecordId(recordId, config.getTableName());
        final NotificationEventModelDao removedEntry = new NotificationEventModelDao(existing, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.REMOVED);
        dao.moveEntryToHistory(removedEntry);
    }

    @Override
    public void removeNotificationFromTransaction(final Connection connection, final Long recordId) {
        final InTransaction.InTransactionHandler<NotificationSqlDao, Void> handler = new InTransaction.InTransactionHandler<NotificationSqlDao, Void>() {
            @Override
            public Void withSqlDao(final NotificationSqlDao transactional) throws Exception {
                final NotificationEventModelDao existing = transactional.getByRecordId(recordId, config.getTableName());
                final NotificationEventModelDao removedEntry = new NotificationEventModelDao(existing, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.REMOVED);
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
