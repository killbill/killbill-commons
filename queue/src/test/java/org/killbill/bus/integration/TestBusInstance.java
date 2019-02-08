/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import org.influxdb.InfluxDB;
import org.killbill.billing.rpc.test.queue.gen.EventMsg;
import org.killbill.billing.rpc.test.queue.gen.InitMsg;
import org.killbill.billing.rpc.test.queue.gen.TestStorageType;
import org.killbill.bus.DefaultPersistentBus;
import org.killbill.bus.InMemoryPersistentBus;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.bus.dao.PersistentBusSqlDao;
import org.killbill.queue.QueueObjectMapper;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;

public class TestBusInstance extends TestQueueInstance implements TestInstance {

    private final Logger logger = LoggerFactory.getLogger(TestBusInstance.class);

    private final TestBusHandler busHandler;
    private final PersistentBus bus;

    public TestBusInstance(final InitMsg initMsg, final InfluxDB influxDB, final String jdbcConnection, final String dbUsername, final String dbPassword) {
        super(initMsg, setupQueueConfig(initMsg), influxDB, jdbcConnection, dbUsername, dbPassword);
        this.busHandler = new TestBusHandler(influxDB, initMsg.getHandlerLatencyMsec());
        this.bus = setupPersistentBus();
    }

    @Override
    public void start() throws EventBusException {
        if (bus != null) {
            bus.start();
            bus.register(busHandler);
        }
        logger.info("Started test instance {}", initMsg.getName());
    }

    @Override
    public void stop() throws Exception {
        logger.info("Stopping test instance {}", initMsg.getName());
        if (bus != null) {
            bus.unregister(busHandler);
            bus.stop();
        }
        super.stop();
    }

    @Override
    public void postEntry(final EventMsg request) throws EventBusException {
        if (bus != null) {
            bus.post(new TestBusEvent(request, initMsg.getName()));
        }
    }

    @Override
    public void insertEntryIntoQueue(final EventMsg request) throws EventBusException {
        final PersistentBusSqlDao dao = dbi.onDemand(PersistentBusSqlDao.class);
        final TestBusEvent entry = new TestBusEvent(request, request.getSource());

        final String json;
        try {
            json = QueueObjectMapper.get().writeValueAsString(entry);
            // We use the source info to override the creator name
            final BusEventModelDao model = new BusEventModelDao(entry.getSource(), clock.getUTCNow(), TestBusEvent.class.getName(), json,
                                                                entry.getUserToken(), entry.getSearchKey1(), entry.getSearchKey2());

            dao.insertEntry(model, queueConfig.getTableName());
        } catch (final JsonProcessingException e) {
            throw new EventBusException("Unable to serialize event " + entry);
        }

    }

    private static PersistentBusConfig setupQueueConfig(final InitMsg initMsg) {

        final Map<String, String> config = new HashMap<>();
        insertNonNullValue(config, "org.killbill.persistent.bus.main.inMemory", String.valueOf(initMsg.getTestStorageType() == TestStorageType.MEMORY));
        insertNonNullValue(config, "org.killbill.persistent.bus.main.queue.mode", initMsg.getQueueMode());
        insertNonNullValue(config, "org.killbill.persistent.bus.main.claimed", initMsg.getQueueClaimed());
        insertNonNullValue(config, "org.killbill.persistent.bus.main.claim.time", initMsg.getQueueClaimedTime());
        insertNonNullValue(config, "org.killbill.persistent.bus.main.sleep", initMsg.getQueueSleep());
        insertNonNullValue(config, "org.killbill.persistent.bus.main.nbThreads", initMsg.getQueueThreads());
        insertNonNullValue(config, "org.killbill.persistent.bus.main.queue.capacity", initMsg.getQueueCapacity());
        insertNonNullValue(config, "org.killbill.persistent.bus.main.reapThreshold", initMsg.getQueueReapThreshold());
        insertNonNullValue(config, "org.killbill.persistent.bus.main.maxReDispatchCount", initMsg.getQueueMaxReDispatchCount());

        final ConfigSource configSource = new ConfigSource() {
            @Override
            public String getString(final String propertyName) {
                return config.get(propertyName);
            }
        };

        final PersistentBusConfig persistentBusConfig = new ConfigurationObjectFactory(configSource).buildWithReplacements(PersistentBusConfig.class,
                                                                                                                           ImmutableMap.<String, String>of("instanceName", "main"));
        return persistentBusConfig;
    }

    public PersistentBus setupPersistentBus() {
        switch (initMsg.getTestStorageType()) {
            case MEMORY:
                return new InMemoryPersistentBus((PersistentBusConfig) queueConfig);
            case DB:
                return new DefaultPersistentBus(dbi, clock, (PersistentBusConfig) queueConfig, metricRegistry, databaseTransactionNotificationApi);
            default:
                return null;
        }
    }

}
