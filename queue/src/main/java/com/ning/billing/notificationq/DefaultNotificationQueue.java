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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.Hostname;
import com.ning.billing.notificationq.api.NotificationEvent;
import com.ning.billing.notificationq.api.NotificationEventBase;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.dao.NotificationEventEntry;
import com.ning.billing.notificationq.dao.NotificationSqlDao;
import com.ning.billing.queue.DBBackedQueue;
import com.ning.billing.queue.DefaultQueueLifecycle;
import com.ning.billing.queue.api.EventEntry.PersistentQueueEntryLifecycleState;
import com.ning.billing.util.clock.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Objects;

public class DefaultNotificationQueue implements NotificationQueue {

    public static final String NOTIFICATION_QUEUE_TABLE_NAME = "notifications";
    public static final String NOTIFICATION_QUEUE_HISTORY_TABLE_NAME = "notifications_history";

    private final DBBackedQueue<NotificationEventEntry> dao;
    private final String svcName;
    private final String queueName;
    private final NotificationQueueHandler handler;
    private final NotificationQueueService notificationQueueService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private volatile boolean isStarted;

    public DefaultNotificationQueue(final String svcName, final String queueName, final NotificationQueueHandler handler,
                                    final DBBackedQueue<NotificationEventEntry> dao, final NotificationQueueService notificationQueueService,
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
    public void recordFutureNotification(final DateTime futureNotificationTime, final NotificationEventBase event, final UUID userToken, final Long searchKey1, final Long searchKey2) throws IOException {
        final String eventJson = objectMapper.writeValueAsString(event);
        final UUID futureUserToken = UUID.randomUUID();
        final Long searchKey2WithNull =  Objects.firstNonNull(searchKey2, new Long(0));
        final NotificationEventEntry notification = new NotificationEventEntry(Hostname.get(), clock.getUTCNow(), event.getClass().getName(), eventJson, userToken, searchKey1, searchKey2WithNull, futureUserToken, futureNotificationTime, getFullQName());
        dao.insertEntry(notification);
    }

    @Override
    public void recordFutureNotificationFromTransaction(final Transmogrifier transmogrifier, final DateTime futureNotificationTime, final NotificationEventBase event,
                                                        final UUID userToken, final Long searchKey1, final Long searchKey2) throws IOException {
        final NotificationSqlDao transactionalNotificationDao = transmogrifier.become(NotificationSqlDao.class);

        final String eventJson = objectMapper.writeValueAsString(event);
        final UUID futureUserToken = UUID.randomUUID();
        final Long searchKey2WithNull =  Objects.firstNonNull(searchKey2, new Long(0));
        final NotificationEventEntry notification = new NotificationEventEntry(Hostname.get(), clock.getUTCNow(), event.getClass().getName(), eventJson, userToken, searchKey1, searchKey2WithNull, futureUserToken, futureNotificationTime, getFullQName());
        dao.insertEntryFromTransaction(transactionalNotificationDao, notification);

    }


    @Override
    public <T extends NotificationEventBase> List<NotificationEvent<T>> getFutureNotificationForSearchKey1(final Class<T> type, final Long searchKey1) {
        return getFutureNotificationsInternal(type, (NotificationSqlDao) dao.getSqlDao(), "search_key1", searchKey1);
    }

    @Override
    public <T extends NotificationEventBase> List<NotificationEvent<T>> getFutureNotificationFromTransactionForSearchKey1(final Class<T> type, final Long searchKey1, final NotificationSqlDao transactionalDao) {
        return getFutureNotificationsInternal(type, (NotificationSqlDao) transactionalDao, "search_key1", searchKey1);
    }

    @Override
    public <T extends NotificationEventBase> List<NotificationEvent<T>> getFutureNotificationForSearchKey2(final Class<T> type, final Long searchKey2) {
        return getFutureNotificationsInternal(type, (NotificationSqlDao) dao.getSqlDao(), "search_key2", searchKey2);
    }

    @Override
    public <T extends NotificationEventBase> List<NotificationEvent<T>> getFutureNotificationFromTransactionForSearchKey2(final Class<T> type, final Long searchKey2, final NotificationSqlDao transactionalDao) {
        return getFutureNotificationsInternal(type, (NotificationSqlDao) transactionalDao, "search_key2", searchKey2);
    }


    private <T extends NotificationEventBase> List<NotificationEvent<T>> getFutureNotificationsInternal(final Class<T> type, final NotificationSqlDao transactionalDao, final String searchKey, final Long searchKeyValue) {

        final List<NotificationEvent<T>> result = new LinkedList<NotificationEvent<T>>();
        final List<NotificationEventEntry> entries = transactionalDao.getReadyQueueEntriesForSearchKey(clock.getUTCNow().toDate(), getFullQName(), searchKeyValue, NOTIFICATION_QUEUE_TABLE_NAME, searchKey);
        for (NotificationEventEntry cur : entries) {
            final T event = (T) DefaultQueueLifecycle.deserializeEvent(cur.getClassName(), objectMapper, cur.getEventJson());
            final NotificationEvent<T> foo = new NotificationEvent<T>(cur.getRecordId(), cur.getUserToken(), cur.getCreatedDate(), cur.getSearchKey1(), cur.getSearchKey2(), event);
            result.add(foo);
        }
        return result;
    }


    @Override
    public void removeNotification(final Long recordId) {
        final NotificationEventEntry existing = dao.getSqlDao().getByRecordId(recordId, NOTIFICATION_QUEUE_TABLE_NAME);
        final NotificationEventEntry removedEntry = new NotificationEventEntry(existing, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.REMOVED);
        dao.moveEntryToHistory(removedEntry);

    }

    @Override
    public void removeNotificationFromTransaction(final Transmogrifier transmogrifier, final Long recordId) {
        final NotificationSqlDao transactional = transmogrifier.become(NotificationSqlDao.class);
        final NotificationEventEntry existing = transactional.getByRecordId(recordId, NOTIFICATION_QUEUE_TABLE_NAME);
        final NotificationEventEntry removedEntry = new NotificationEventEntry(existing, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.REMOVED);
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
    public String getHostName() {
        return null;
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
