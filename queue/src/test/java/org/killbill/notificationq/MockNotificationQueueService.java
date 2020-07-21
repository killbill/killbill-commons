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

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

import javax.inject.Inject;

import org.killbill.CreatorName;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueConfig;
import org.killbill.notificationq.dao.NotificationEventModelDao;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.dispatching.CallableCallbackBase;
import org.skife.jdbi.v2.DBI;

import com.codahale.metrics.MetricRegistry;


public class MockNotificationQueueService extends NotificationQueueServiceBase {

    @Inject
    public MockNotificationQueueService(final Clock clock, final NotificationQueueConfig config, final DBI dbi, final MetricRegistry metricRegistry) {
        super(clock, config, dbi, metricRegistry);
    }

    @Override
    protected NotificationQueue createNotificationQueueInternal(final String svcName, final String queueName,
                                                                final NotificationQueueHandler handler) {
        return new MockNotificationQueue(clock, svcName, queueName, handler, this);
    }

    @Override
    public DispatchResultMetrics doDispatchEvents() {
        int retry = 2;
        do {
            try {
                int result = 0;
                for (final String queueName : queues.keySet()) {
                    final NotificationQueue cur = queues.get(queueName);
                    if (cur != null) {
                        result += doProcessEventsForQueue((MockNotificationQueue) cur);
                    }
                }
                return new DispatchResultMetrics(result, -1);
            } catch (final ConcurrentModificationException e) {
                retry--;
            }
        } while (retry > 0);
        return new DispatchResultMetrics(0, -1);
    }

    private int doProcessEventsForQueue(final MockNotificationQueue queue) {


        int result = 0;
        final List<NotificationEventModelDao> processedNotifications = new ArrayList<NotificationEventModelDao>();
        final List<NotificationEventModelDao> oldNotifications = new ArrayList<NotificationEventModelDao>();

        final List<NotificationEventModelDao> readyNotifications = queue.getReadyNotifications();
        for (final NotificationEventModelDao cur : readyNotifications) {
            final NotificationEvent key = CallableCallbackBase.deserializeEvent(cur, objectMapper);
            queue.getHandler().handleReadyNotification(key, cur.getEffectiveDate(), cur.getFutureUserToken(), cur.getSearchKey1(), cur.getSearchKey2());


            final NotificationEventModelDao processedNotification = new NotificationEventModelDao(cur.getRecordId(), CreatorName.get(), CreatorName.get(), clock.getUTCNow(),
                                                                                                  getClock().getUTCNow().plus(CLAIM_TIME_MS),
                                                                                                  PersistentQueueEntryLifecycleState.PROCESSED, cur.getClassName(),
                                                                                                  cur.getEventJson(), 0L, cur.getUserToken(), cur.getSearchKey1(), cur.getSearchKey2(),
                                                                                                  cur.getFutureUserToken(), cur.getEffectiveDate(), "MockQueue");
            oldNotifications.add(cur);
            processedNotifications.add(processedNotification);
            result++;
        }

        queue.markProcessedNotifications(oldNotifications, processedNotifications);
        return result;
    }
}
