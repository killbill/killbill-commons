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

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.Hostname;
import com.ning.billing.notificationq.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.queue.DefaultQueueLifecycle;
import com.ning.billing.util.clock.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Objects;

public class MockNotificationQueue implements NotificationQueue {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String hostname;
    private final TreeSet<Notification> notifications;
    private final Clock clock;
    private final String svcName;
    private final String queueName;
    private final NotificationQueueHandler handler;
    private final MockNotificationQueueService queueService;

    private volatile boolean isStarted;

    public MockNotificationQueue(final Clock clock, final String svcName, final String queueName, final NotificationQueueHandler handler, final MockNotificationQueueService mockNotificationQueueService) {

        this.svcName = svcName;
        this.queueName = queueName;
        this.handler = handler;
        this.clock = clock;
        this.hostname = Hostname.get();
        this.queueService = mockNotificationQueueService;

        notifications = new TreeSet<Notification>(new Comparator<Notification>() {
            @Override
            public int compare(final Notification o1, final Notification o2) {
                if (o1.getEffectiveDate().equals(o2.getEffectiveDate())) {
                    return o1.getNotificationKey().compareTo(o2.getNotificationKey());
                } else {
                    return o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
                }
            }
        });
    }

    @Override
    public void recordFutureNotification(final DateTime futureNotificationTime, final NotificationKey notificationKey, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) throws IOException {
        final String json = objectMapper.writeValueAsString(notificationKey);
        final Long tenantRecordIdWithNull =  Objects.firstNonNull(tenantRecordId, new Long(0));
        final Notification notification = new DefaultNotification("MockQueue", hostname, notificationKey.getClass().getName(), json, userToken,
                                                                  UUID.randomUUID(), futureNotificationTime, accountRecordId, tenantRecordIdWithNull);
        synchronized (notifications) {
            notifications.add(notification);
        }
    }

    @Override
    public void recordFutureNotificationFromTransaction(final Transmogrifier transmogrifier, final DateTime futureNotificationTime, final NotificationKey notificationKey, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) throws IOException {
        recordFutureNotification(futureNotificationTime, notificationKey, userToken, accountRecordId, tenantRecordId);
    }


    @Override
    public <T extends NotificationKey> Map<Notification, T> getFutureNotificationsForAccountAndType(final Class<T> type, final Long accountRecordId) {
        return getFutureNotificationsForAccountAndTypeFromTransaction(type, accountRecordId, null);
    }

    @Override
    public <T extends NotificationKey> Map<Notification, T> getFutureNotificationsForAccountAndTypeFromTransaction(final Class<T> type, final Long accountRecordId, final Transmogrifier transmogrifier) {
        final Map<Notification, T> result = new HashMap<Notification, T>();
        synchronized (notifications) {
            for (final Notification notification : notifications) {
                if (notification.getAccountRecordId().equals(accountRecordId) &&
                    type.getName().equals(notification.getNotificationKeyClass()) &&
                    notification.getEffectiveDate().isAfter(clock.getUTCNow())) {
                    result.put(notification, (T) DefaultQueueLifecycle.deserializeEvent(notification.getNotificationKeyClass(), objectMapper, notification.getNotificationKey()));
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
            for (final Notification cur : notifications) {
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

    public List<Notification> getReadyNotifications() {
        final List<Notification> readyNotifications = new ArrayList<Notification>();
        synchronized (notifications) {
            for (final Notification cur : notifications) {
                if (cur.isAvailableForProcessing(clock.getUTCNow())) {
                    readyNotifications.add(cur);
                }
            }
        }
        return readyNotifications;
    }

    public void markProcessedNotifications(final List<Notification> toBeremoved, final List<Notification> toBeAdded) {
        synchronized (notifications) {
            notifications.removeAll(toBeremoved);
            notifications.addAll(toBeAdded);
        }
    }

}
