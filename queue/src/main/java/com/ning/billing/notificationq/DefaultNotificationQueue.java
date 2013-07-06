/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.notificationq;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.Hostname;
import com.ning.billing.notificationq.api.NotificationEvent;
import com.ning.billing.notificationq.api.NotificationEventWithMetadata;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.dao.NotificationEventModelDao;
import com.ning.billing.notificationq.dao.NotificationSqlDao;
import com.ning.billing.queue.DBBackedQueue;
import com.ning.billing.queue.DefaultQueueLifecycle;
import com.ning.billing.queue.api.PersistentQueueEntryLifecycleState;
import com.ning.billing.util.clock.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Objects;

public class DefaultNotificationQueue implements NotificationQueue {

    public static final String NOTIFICATION_QUEUE_TABLE_NAME = "notifications";
    public static final String NOTIFICATION_QUEUE_HISTORY_TABLE_NAME = "notifications_history";

    private final DBBackedQueue<NotificationEventModelDao> dao;
    private final String svcName;
    private final String queueName;
    private final NotificationQueueHandler handler;
    private final NotificationQueueService notificationQueueService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private volatile boolean isStarted;

    public DefaultNotificationQueue(final String svcName, final String queueName, final NotificationQueueHandler handler,
                                    final DBBackedQueue<NotificationEventModelDao> dao, final NotificationQueueService notificationQueueService,
                                    final Clock clock) {
        this.svcName = svcName;
        this.queueName = queueName;
        this.handler = handler;
        this.dao = dao;
        this.notificationQueueService = notificationQueueService;
        this.objectMapper = new ObjectMapper();
        this.clock = clock;
    }


    @Override
    public void recordFutureNotification(final DateTime futureNotificationTime, final NotificationEvent event, final UUID userToken, final Long searchKey1, final Long searchKey2) throws IOException {
        final String eventJson = objectMapper.writeValueAsString(event);
        final UUID futureUserToken = UUID.randomUUID();
        final Long searchKey2WithNull =  Objects.firstNonNull(searchKey2, new Long(0));
        final NotificationEventModelDao notification = new NotificationEventModelDao(Hostname.get(), clock.getUTCNow(), event.getClass().getName(), eventJson, userToken, searchKey1, searchKey2WithNull, futureUserToken, futureNotificationTime, getFullQName());
        dao.insertEntry(notification);
    }

    @Override
    public void recordFutureNotificationFromTransaction(final Transmogrifier transmogrifier, final DateTime futureNotificationTime, final NotificationEvent event,
                                                        final UUID userToken, final Long searchKey1, final Long searchKey2) throws IOException {
        final NotificationSqlDao transactionalNotificationDao = transmogrifier.become(NotificationSqlDao.class);

        final String eventJson = objectMapper.writeValueAsString(event);
        final UUID futureUserToken = UUID.randomUUID();
        final Long searchKey2WithNull =  Objects.firstNonNull(searchKey2, new Long(0));
        final NotificationEventModelDao notification = new NotificationEventModelDao(Hostname.get(), clock.getUTCNow(), event.getClass().getName(), eventJson, userToken, searchKey1, searchKey2WithNull, futureUserToken, futureNotificationTime, getFullQName());
        dao.insertEntryFromTransaction(transactionalNotificationDao, notification);

    }


    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKey1(final Class<T> type, final Long searchKey1) {
        return getFutureNotificationsInternal(type, (NotificationSqlDao) dao.getSqlDao(), "search_key1", searchKey1);
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKey1(final Class<T> type, final Long searchKey1, final Transmogrifier transmogrifier) {
        final NotificationSqlDao transactionalNotificationDao = transmogrifier.become(NotificationSqlDao.class);
        return getFutureNotificationsInternal(type, transactionalNotificationDao, "search_key1", searchKey1);
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKey2(final Class<T> type, final Long searchKey2) {
        return getFutureNotificationsInternal(type, (NotificationSqlDao) dao.getSqlDao(), "search_key2", searchKey2);
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKey2(final Class<T> type, final Long searchKey2, final Transmogrifier transmogrifier) {
        final NotificationSqlDao transactionalNotificationDao = transmogrifier.become(NotificationSqlDao.class);
        return getFutureNotificationsInternal(type, transactionalNotificationDao, "search_key2", searchKey2);
    }


    private <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationsInternal(final Class<T> type, final NotificationSqlDao transactionalDao, final String searchKey, final Long searchKeyValue) {

        final List<NotificationEventWithMetadata<T>> result = new LinkedList<NotificationEventWithMetadata<T>>();
        final List<NotificationEventModelDao> entries = transactionalDao.getReadyQueueEntriesForSearchKey(clock.getUTCNow().toDate(), getFullQName(), searchKeyValue, NOTIFICATION_QUEUE_TABLE_NAME, searchKey);
        for (NotificationEventModelDao cur : entries) {
            final T event = (T) DefaultQueueLifecycle.deserializeEvent(cur.getClassName(), objectMapper, cur.getEventJson());
            final NotificationEventWithMetadata<T> foo = new NotificationEventWithMetadata<T>(cur.getRecordId(), cur.getUserToken(), cur.getCreatedDate(), cur.getSearchKey1(), cur.getSearchKey2(), event);
            result.add(foo);
        }
        return result;
    }


    @Override
    public void removeNotification(final Long recordId) {
        final NotificationEventModelDao existing = dao.getSqlDao().getByRecordId(recordId, NOTIFICATION_QUEUE_TABLE_NAME);
        final NotificationEventModelDao removedEntry = new NotificationEventModelDao(existing, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.REMOVED);
        dao.moveEntryToHistory(removedEntry);

    }

    @Override
    public void removeNotificationFromTransaction(final Transmogrifier transmogrifier, final Long recordId) {
        final NotificationSqlDao transactional = transmogrifier.become(NotificationSqlDao.class);
        final NotificationEventModelDao existing = transactional.getByRecordId(recordId, NOTIFICATION_QUEUE_TABLE_NAME);
        final NotificationEventModelDao removedEntry = new NotificationEventModelDao(existing, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.REMOVED);
        dao.moveEntryToHistoryFromTransaction(transactional, removedEntry);
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
    public void startQueue() {
        notificationQueueService.startQueue();
        isStarted = true;
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
