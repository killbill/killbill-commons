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

package org.killbill.notificationq;

import com.codahale.metrics.MetricRegistry;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueConfig;
import org.skife.jdbi.v2.IDBI;

import javax.inject.Inject;
import javax.inject.Named;


public class DefaultNotificationQueueService extends NotificationQueueServiceBase {

    @Inject
    public DefaultNotificationQueueService(@Named(QUEUE_NAME) final IDBI dbi, final Clock clock, final NotificationQueueConfig config, final MetricRegistry metricRegistry) {
        super(clock, config, dbi, metricRegistry);
    }

    @Override
    protected NotificationQueue createNotificationQueueInternal(final String svcName,
                                                                final String queueName,
                                                                final NotificationQueueHandler handler) {
        return new DefaultNotificationQueue(svcName, queueName, handler, dao, this, clock, config);
    }
}
