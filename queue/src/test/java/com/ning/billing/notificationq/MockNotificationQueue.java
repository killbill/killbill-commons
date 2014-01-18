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
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.Hostname;
import com.ning.billing.notificationq.api.NotificationEvent;
import com.ning.billing.notificationq.api.NotificationEventWithMetadata;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.notificationq.dao.NotificationEventModelDao;
import com.ning.billing.queue.DefaultQueueLifecycle;
import com.ning.billing.queue.api.PersistentQueueEntryLifecycleState;
import com.ning.billing.clock.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Objects;

public class MockNotificationQueue implements NotificationQueue {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String hostname;
    private final TreeSet<NotificationEventModelDao> notifications;
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

        notifications = new TreeSet<NotificationEventModelDao>(new Comparator<NotificationEventModelDao>() {
            @Override
            public int compare(final NotificationEventModelDao o1, final NotificationEventModelDao o2) {
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
        final Long searchKey2WithNull = Objects.firstNonNull(searchKey2, new Long(0));
        final NotificationEventModelDao notification = new NotificationEventModelDao(recordIds.incrementAndGet(), "MockQueue", hostname, clock.getUTCNow(), null, PersistentQueueEntryLifecycleState.AVAILABLE,
                                                                               eventJson.getClass().getName(), json, 0L, userToken, searchKey1, searchKey2WithNull, UUID.randomUUID(),
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
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKey1(final Class<T> type, final Long searchKey1) {
        return null;
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKey1(final Class<T> type, final Long searchKey1, final Transmogrifier transmogrifier) {
        return null;
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKey2(final Class<T> type, final Long searchKey2) {
        return null;
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKey2(final Class<T> type, final Long searchKey2, final Transmogrifier transmogrifier) {
        return null;
    }

    @Override
    public int getReadyNotificationEntriesForSearchKey1(Long searchKey1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getReadyNotificationEntriesForSearchKey2(Long searchKey2) {
        throw new UnsupportedOperationException();
    }


    private <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationsInternal(final Class<T> type, final Long searchKey1, final Transmogrifier transmogrifier) {
        final List<NotificationEventWithMetadata<T>> result = new ArrayList<NotificationEventWithMetadata<T>>();
        synchronized (notifications) {
            for (final NotificationEventModelDao notification : notifications) {
                if (notification.getSearchKey1().equals(searchKey1) &&
                    type.getName().equals(notification.getClassName()) &&
                    notification.getEffectiveDate().isAfter(clock.getUTCNow())) {
                    final T event = (T) DefaultQueueLifecycle.deserializeEvent(notification.getClassName(), objectMapper, notification.getEventJson());
                    final NotificationEventWithMetadata<T> foo = new NotificationEventWithMetadata<T>(notification.getRecordId(), notification.getUserToken(), notification.getCreatedDate(), notification.getSearchKey1(), notification.getSearchKey2(), event,
                                                                                                      notification.getFutureUserToken(), notification.getEffectiveDate(), notification.getQueueName());
                    result.add(foo);
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
            for (final NotificationEventModelDao cur : notifications) {
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

    private Logger log = LoggerFactory.getLogger("MockNotificationQueue");

    public List<NotificationEventModelDao> getReadyNotifications() {
        final List<NotificationEventModelDao> readyNotifications = new ArrayList<NotificationEventModelDao>();
        synchronized (notifications) {
            for (final NotificationEventModelDao cur : notifications) {
                if ( cur.getEffectiveDate().isBefore(clock.getUTCNow()) && cur.isAvailableForProcessing(clock.getUTCNow())) {

                    log.info("MockNotificationQ getReadyNotifications found notification: NOW = " + clock.getUTCNow() + " recordId = " + cur.getRecordId() +
                            ", state = " + cur.getProcessingState() + ", effectiveDate = " + cur.getEffectiveDate());
                    readyNotifications.add(cur);
                }
            }
        }
        return readyNotifications;
    }

    public void markProcessedNotifications(final List<NotificationEventModelDao> toBeremoved, final List<NotificationEventModelDao> toBeAdded) {
        synchronized (notifications) {
            notifications.removeAll(toBeremoved);
            notifications.addAll(toBeAdded);
        }
    }

}
