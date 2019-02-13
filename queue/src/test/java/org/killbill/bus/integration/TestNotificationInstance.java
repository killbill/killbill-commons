/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.bus.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.influxdb.InfluxDB;
import org.killbill.billing.rpc.test.queue.gen.EventMsg;
import org.killbill.billing.rpc.test.queue.gen.InitMsg;
import org.killbill.billing.rpc.test.queue.gen.TestStorageType;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.notificationq.DefaultNotificationQueueService;
import org.killbill.notificationq.MockNotificationQueue;
import org.killbill.notificationq.MockNotificationQueueService;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueConfig;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.killbill.notificationq.dao.NotificationEventModelDao;
import org.killbill.notificationq.dao.NotificationSqlDao;
import org.killbill.queue.QueueObjectMapper;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;

public class TestNotificationInstance extends TestQueueInstance implements TestInstance {

    private final Logger logger = LoggerFactory.getLogger(TestNotificationInstance.class);

    private static final String SVC_NAME = "test-svc";

    private final TestNotificationQueueHandler notificationQueueHandler;
    private final NotificationQueue notificationQueue;

    private NotificationQueueService notificationQueueService;

    public TestNotificationInstance(final InitMsg initMsg, final InfluxDB influxDB, final String jdbcConnection, final String dbUsername, final String dbPassword) {
        super(initMsg, setupQueueConfig(initMsg), influxDB, jdbcConnection, dbUsername, dbPassword);
        this.notificationQueueHandler = new TestNotificationQueueHandler(influxDB, initMsg.getHandlerLatencyMsec());
        this.notificationQueue = setupNotificationQ();

    }

    @Override
    public void start() {
        if (notificationQueue != null) {
            notificationQueue.startQueue();
        }
        logger.info("Started test instance {}", initMsg.getName());
    }

    @Override
    public void stop() throws Exception {
        logger.info("Stopping test instance {}", initMsg.getName());
        if (notificationQueue != null) {
            notificationQueue.stopQueue();
            notificationQueueService.deleteNotificationQueue(SVC_NAME, initMsg.getName());
        }
        super.stop();
    }

    @Override
    public void postEntry(final EventMsg request) throws Exception {
        if (notificationQueue != null) {
            notificationQueue.recordFutureNotification(clock.getUTCNow(), new TestNotificationEvent(request, initMsg.getName()), null, request.getSearchKey1(), request.getSearchKey2());
        }
    }

    @Override
    public void insertEntryIntoQueue(final EventMsg request) throws EventBusException {
        final NotificationSqlDao dao = dbi.onDemand(NotificationSqlDao.class);

        final TestNotificationEvent entry = new TestNotificationEvent(request.getSource());

        final String json;
        try {
            json = QueueObjectMapper.get().writeValueAsString(entry);

            // We use the source info to override the creator name
            final UUID userToken = null;//UUID.fromString(request.getUserToken());
            final NotificationEventModelDao model = new NotificationEventModelDao(entry.getSource(), clock.getUTCNow(), TestNotificationEvent.class.getName(), json,
                                                                                  userToken, request.getSearchKey1(), request.getSearchKey2(), userToken, clock.getUTCNow(), initMsg.getName());

            dao.insertEntry(model, queueConfig.getTableName());
        } catch (final JsonProcessingException e) {
            throw new EventBusException("Unable to serialize event " + entry);
        }

    }

    private static NotificationQueueConfig setupQueueConfig(final InitMsg initMsg) {

        final Map<String, String> config = new HashMap<>();
        insertNonNullValue(config, "org.killbill.notificationq.main.inMemory", String.valueOf(initMsg.getTestStorageType() == TestStorageType.MEMORY));
        insertNonNullValue(config, "org.killbill.notificationq.main.queue.mode", initMsg.getQueueMode());
        insertNonNullValue(config, "org.killbill.notificationq.main.claimed", initMsg.getQueueClaimed());
        insertNonNullValue(config, "org.killbill.notificationq.main.claim.time", initMsg.getQueueClaimedTime());
        insertNonNullValue(config, "org.killbill.notificationq.main.sleep", initMsg.getQueueSleep());
        insertNonNullValue(config, "org.killbill.notificationq.main.notification.nbThreads", initMsg.getQueueThreads());
        insertNonNullValue(config, "org.killbill.notificationq.main.queue.capacity", initMsg.getQueueCapacity());
        insertNonNullValue(config, "org.killbill.notificationq.main.reapThreshold", initMsg.getQueueReapThreshold());
        insertNonNullValue(config, "org.killbill.notificationq.main.maxReDispatchCount", initMsg.getQueueMaxReDispatchCount());

        final ConfigSource configSource = new ConfigSource() {
            @Override
            public String getString(final String propertyName) {
                return config.get(propertyName);
            }
        };

        final NotificationQueueConfig notificationQueueConfig = new ConfigurationObjectFactory(configSource).buildWithReplacements(NotificationQueueConfig.class,
                                                                                                                                   ImmutableMap.<String, String>of("instanceName", "main"));
        return notificationQueueConfig;
    }

    public NotificationQueue setupNotificationQ() {

        switch (initMsg.getTestStorageType()) {

            case MEMORY:
                final MockNotificationQueueService mockQueueService = new MockNotificationQueueService(clock, (NotificationQueueConfig) queueConfig, dbi, metricRegistry);
                this.notificationQueueService = mockQueueService;
                return new MockNotificationQueue(clock, SVC_NAME, initMsg.getName(), notificationQueueHandler, mockQueueService);

            case DB:
                final DefaultNotificationQueueService queueService = new DefaultNotificationQueueService(dbi, clock, (NotificationQueueConfig) queueConfig, metricRegistry);
                this.notificationQueueService = queueService;
                try {
                    return queueService.createNotificationQueue(SVC_NAME,
                                                                initMsg.getName(),
                                                                notificationQueueHandler);
                } catch (NotificationQueueAlreadyExists e) {
                    throw new RuntimeException(e);
                }

            default:
                return null;
        }
    }
}
