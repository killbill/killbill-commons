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

import java.util.Properties;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;

import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueConfig;
import org.killbill.queue.InTransaction;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.config.SimplePropertyConfigSource;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;

/**
 * A factory to create notification queues.
 * An application will typically have a single instance and call <code>createNotificationQueue</code>
 * to create one or several queues.
 */
public class DefaultNotificationQueueService extends NotificationQueueServiceBase {

    private final DBI dbi;

    /**
     * @param idbi           a DBI instance from the killbill-jdbi jar
     * @param clock          a clock instance from the killbill-clock jar
     * @param config         queues configuration
     * @param metricRegistry DropWizard metrics registry instance
     */
    @Inject
    public DefaultNotificationQueueService(@Named(QUEUE_NAME) final IDBI idbi, final Clock clock, final NotificationQueueConfig config, final MetricRegistry metricRegistry) {
        super(clock, config, idbi, metricRegistry);
        this.dbi = (DBI) idbi;
    }

    /**
     * Simple constructor when the DBI instance, clock or registry objects don't need to be configured
     *
     * @param dataSource JDBC datasource
     * @param properties configuration properties
     */
    public DefaultNotificationQueueService(final DataSource dataSource, final Properties properties) {
        this(InTransaction.buildDDBI(dataSource),
             new DefaultClock(),
             new ConfigurationObjectFactory(new SimplePropertyConfigSource(properties)).buildWithReplacements(NotificationQueueConfig.class, ImmutableMap.<String, String>of("instanceName", "main")),
             new MetricRegistry());
    }

    @Override
    protected NotificationQueue createNotificationQueueInternal(final String svcName,
                                                                final String queueName,
                                                                final NotificationQueueHandler handler) {
        return new DefaultNotificationQueue(svcName, queueName, handler, dbi, dao, this, clock, config);
    }
}
