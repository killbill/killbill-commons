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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.Hostname;
import com.ning.billing.notificationq.api.NotificationEvent;
import com.ning.billing.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.dao.NotificationEventEntry;
import com.ning.billing.queue.DefaultQueueLifecycle;
import com.ning.billing.queue.api.EventEntry.PersistentQueueEntryLifecycleState;
import com.ning.billing.util.clock.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Objects;

public class MockNotificationQueue implements NotificationQueue {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String hostname;
    private final TreeSet<NotificationEventEntry> notifications;
    private final Clock clock;
    private final String svcName;
    private final String queueName;
    private final NotificationQueueHandler handler;
    private final MockNotificationQueueService queueService;

    private AtomicLong recordIds;

    private volatile boolean isStarted;

    public MockNotificationQueue(final Clock clock, final String svcName, final String queueName, final NotificationQueueHandler handler, final MockNotificationQueueService mockNotificationQueueService) {

        this.svcName = svcName;
        this.queueName = queueName;
        this.handler = handler;
        this.clock = clock;
        this.hostname = Hostname.get();
        this.queueService = mockNotificationQueueService;

        this.recordIds = new AtomicLong();

        notifications = new TreeSet<NotificationEventEntry>(new Comparator<NotificationEventEntry>() {
            @Override
            public int compare(final NotificationEventEntry o1, final NotificationEventEntry o2) {
                if (o1.getEffectiveDate().equals(o2.getEffectiveDate())) {
                    return o1.getRecordId().compareTo(o2.getRecordId());
                } else {
                    return o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
                }
            }
        });
    }

    @Override
    public void recordFutureNotification(final DateTime futureNotificationTime, final NotificationEvent eventJson, final UUID userToken, final Long searchKey1, final Long searchKey2) throws IOException {
        final String json = objectMapper.writeValueAsString(eventJson);
        final Long searchKey2WithNull =  Objects.firstNonNull(searchKey2, new Long(0));
        final NotificationEventEntry notification = new NotificationEventEntry(recordIds.incrementAndGet(), "MockQueue", hostname, clock.getUTCNow(),  null, PersistentQueueEntryLifecycleState.AVAILABLE,
                                                                               eventJson.getClass().getName(), json, userToken, searchKey1, searchKey2WithNull, UUID.randomUUID(),
                                                                               futureNotificationTime, "MockQueue");

        synchronized (notifications) {
            notifications.add(notification);
        }
    }

    @Override
    public void recordFutureNotificationFromTransaction(final Transmogrifier transmogrifier, final DateTime futureNotificationTime, final NotificationEvent eventJson, final UUID userToken, final Long searchKey1, final Long searchKey2) throws IOException {
        recordFutureNotification(futureNotificationTime, eventJson, userToken, searchKey1, searchKey2);
    }


    @Override
    public <T extends NotificationEvent> Map<NotificationEventEntry, T> getFutureNotificationsForAccountAndType(final Class<T> type, final Long searchKey1) {
        return getFutureNotificationsForAccountAndTypeFromTransaction(type, searchKey1, null);
    }

    @Override
    public <T extends NotificationEvent> Map<NotificationEventEntry, T> getFutureNotificationsForAccountAndTypeFromTransaction(final Class<T> type, final Long searchKey1, final Transmogrifier transmogrifier) {
        final Map<NotificationEventEntry, T> result = new HashMap<NotificationEventEntry, T>();
        synchronized (notifications) {
            for (final NotificationEventEntry notification : notifications) {
                if (notification.getSearchKey1().equals(searchKey1) &&
                    type.getName().equals(notification.getClassName()) &&
                    notification.getEffectiveDate().isAfter(clock.getUTCNow())) {
                    result.put(notification, (T) DefaultQueueLifecycle.deserializeEvent(notification.getClassName(), objectMapper, notification.getEventJson()));
                }
            }
        }
        return result;
    }



    @Override
    public void removeNotification(final Long recordId) {
        removeNotificationFromTransaction(null, recordId);
    }

    @Override
    public void removeNotificationFromTransaction(final Transmogrifier transmogrifier, final Long recordId) {
        synchronized (notifications) {
            for (final NotificationEventEntry cur : notifications) {
                if (cur.getRecordId().equals(recordId)) {
                    notifications.remove(cur);
                    break;
                }
            }
        }
    }

    @Override
    public String getFullQName() {
        return NotificationQueueDispatcher.getCompositeName(svcName, queueName);
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
        isStarted = true;
        queueService.startQueue();
    }

    @Override
    public void stopQueue() {
        isStarted = false;
        queueService.stopQueue();
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    public List<NotificationEventEntry> getReadyNotifications() {
        final List<NotificationEventEntry> readyNotifications = new ArrayList<NotificationEventEntry>();
        synchronized (notifications) {
            for (final NotificationEventEntry cur : notifications) {
                if (cur.isAvailableForProcessing(clock.getUTCNow())) {
                    readyNotifications.add(cur);
                }
            }
        }
        return readyNotifications;
    }

    public void markProcessedNotifications(final List<NotificationEventEntry> toBeremoved, final List<NotificationEventEntry> toBeAdded) {
        synchronized (notifications) {
            notifications.removeAll(toBeremoved);
            notifications.addAll(toBeAdded);
        }
    }

}
