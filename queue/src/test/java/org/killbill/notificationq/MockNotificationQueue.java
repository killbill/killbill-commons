/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.killbill.notificationq.dao.NotificationEventModelDao;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.dispatching.CallableCallbackBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class MockNotificationQueue implements NotificationQueue {

    private final Logger log = LoggerFactory.getLogger("MockNotificationQueue");

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String hostname;
    private final TreeSet<NotificationEventModelDao> notifications;
    private final Clock clock;
    private final String svcName;
    private final String queueName;
    private final NotificationQueueHandler handler;
    private final MockNotificationQueueService queueService;

    private final AtomicLong recordIds;
    private volatile boolean isStarted;

    public MockNotificationQueue(final Clock clock, final String svcName, final String queueName, final NotificationQueueHandler handler, final MockNotificationQueueService mockNotificationQueueService) {

        this.svcName = svcName;
        this.queueName = queueName;
        this.handler = handler;
        this.clock = clock;
        this.hostname = CreatorName.get();
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
        final Long searchKey2WithNull = Objects.requireNonNullElse(searchKey2, 0L);
        final NotificationEventModelDao notification = new NotificationEventModelDao(recordIds.incrementAndGet(), "MockQueue", hostname, clock.getUTCNow(), null, PersistentQueueEntryLifecycleState.AVAILABLE,
                                                                                     eventJson.getClass().getName(), json, 0L, userToken, searchKey1, searchKey2WithNull, UUID.randomUUID(),
                                                                                     futureNotificationTime, "MockQueue");

        synchronized (notifications) {
            notifications.add(notification);
        }
    }

    @Override
    public void recordFutureNotificationFromTransaction(final Connection connection, final DateTime futureNotificationTime, final NotificationEvent eventJson, final UUID userToken, final Long searchKey1, final Long searchKey2) throws IOException {
        recordFutureNotification(futureNotificationTime, eventJson, userToken, searchKey1, searchKey2);
    }

    @Override
    public void updateFutureNotification(final Long recordId, final NotificationEvent eventJson, final Long searchKey1, final Long searchKey2) throws IOException {
        return;
    }

    @Override
    public void updateFutureNotificationFromTransaction(final Connection connection, final Long recordId, final NotificationEvent eventJson, final Long searchKey1, final Long searchKey2) throws IOException {
        return;
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return null;
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        return null;
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2) {
        return null;
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2, final Connection connection) {
        return null;
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getInProcessingNotifications() {
        return null;
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return null;
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        return null;
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2) {
        return null;
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationFromTransactionForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2, final Connection connection) {
        return null;
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getHistoricalNotificationForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return null;
    }

    @Override
    public <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getHistoricalNotificationForSearchKey2(final DateTime minEffectiveDate, final Long searchKey2) {
        return null;
    }

    private <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationsInternal(final Class<T> type, final Long searchKey1, final Connection connection) {
        final List<NotificationEventWithMetadata<T>> result = new ArrayList<NotificationEventWithMetadata<T>>();
        synchronized (notifications) {
            for (final NotificationEventModelDao notification : notifications) {
                if (notification.getSearchKey1().equals(searchKey1) &&
                    type.getName().equals(notification.getClassName()) &&
                    notification.getEffectiveDate().isAfter(clock.getUTCNow())) {
                    final T event = CallableCallbackBase.deserializeEvent(notification, objectMapper);
                    final NotificationEventWithMetadata<T> foo = new NotificationEventWithMetadata<T>(notification.getRecordId(), notification.getUserToken(), notification.getCreatedDate(), notification.getSearchKey1(), notification.getSearchKey2(), event,
                                                                                                      notification.getFutureUserToken(), notification.getEffectiveDate(), notification.getQueueName());
                    result.add(foo);
                }
            }
        }
        return result;
    }

    @Override
    public long getNbReadyEntries(final DateTime maxEffectiveDate) {
        return 0;
    }

    @Override
    public void removeNotification(final Long recordId) {
        removeNotificationFromTransaction(null, recordId);
    }

    @Override
    public void removeNotificationFromTransaction(final Connection connection, final Long recordId) {
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
    public void removeFutureNotificationsForSearchKeys(final Long searchKey1, final Long searchKey2) {
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
    public boolean initQueue() {
        return true;
    }

    @Override
    public boolean startQueue() {
        isStarted = true;
        queueService.startQueue();
        return true;
    }

    @Override
    public boolean stopQueue() {
        isStarted = false;
        return queueService.stopQueue();
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    public List<NotificationEventModelDao> getReadyNotifications() {
        final List<NotificationEventModelDao> readyNotifications = new ArrayList<NotificationEventModelDao>();
        synchronized (notifications) {
            for (final NotificationEventModelDao cur : notifications) {
                if (cur.getEffectiveDate().isBefore(clock.getUTCNow()) && cur.isAvailableForProcessing(clock.getUTCNow())) {

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
