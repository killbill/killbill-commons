/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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
import java.util.List;

import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueConfig;
import org.killbill.notificationq.api.NotificationQueueService;
import org.skife.jdbi.v2.IDBI;

import com.codahale.metrics.MetricRegistry;


public abstract class NotificationQueueServiceBase extends NotificationQueueDispatcher implements NotificationQueueService {

    public NotificationQueueServiceBase(final Clock clock, final NotificationQueueConfig config, final IDBI dbi, final MetricRegistry metricRegistry) {
        super(clock, config, dbi, metricRegistry);
    }

    @Override
    public NotificationQueue createNotificationQueue(final String svcName,
                                                     final String queueName,
                                                     final NotificationQueueHandler handler) throws NotificationQueueAlreadyExists {
        if (svcName == null || queueName == null || handler == null) {
            throw new RuntimeException("Need to specify all parameters");
        }

        final String compositeName = getCompositeName(svcName, queueName);
        NotificationQueue result;
        synchronized (queues) {
            result = queues.get(compositeName);
            if (result != null) {
                throw new NotificationQueueAlreadyExists(String.format("Queue for svc %s and name %s already exist",
                                                                       svcName, queueName));
            }
            result = createNotificationQueueInternal(svcName, queueName, handler);
            queues.put(compositeName, result);
        }
        return result;
    }

    @Override
    public NotificationQueue getNotificationQueue(final String svcName,
                                                  final String queueName) throws NoSuchNotificationQueue {

        final NotificationQueue result;
        final String compositeName = getCompositeName(svcName, queueName);
        synchronized (queues) {
            result = queues.get(compositeName);
            if (result == null) {
                throw new NoSuchNotificationQueue(String.format("Queue for svc %s and name %s does not exist",
                                                                svcName, queueName));
            }
        }
        return result;
    }

    public void deleteNotificationQueue(final String svcName, final String queueName)
            throws NoSuchNotificationQueue {
        final String compositeName = getCompositeName(svcName, queueName);
        synchronized (queues) {
            final NotificationQueue result = queues.get(compositeName);
            if (result == null) {
                throw new NoSuchNotificationQueue(String.format("Queue for svc %s and name %s does not exist",
                                                                svcName, queueName));
            }
            queues.remove(compositeName);
        }
    }

    @Override
    public List<NotificationQueue> getNotificationQueues() {
        return new ArrayList<NotificationQueue>(queues.values());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("NotificationQueueServiceBase");
        sb.append("{queues=").append(queues);
        sb.append('}');
        return sb.toString();
    }

    protected abstract NotificationQueue createNotificationQueueInternal(String svcName,
                                                                         String queueName, NotificationQueueHandler handler);
}
