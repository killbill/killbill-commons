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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Point;
import org.killbill.billing.rpc.test.queue.gen.EventMsg;
import org.killbill.billing.rpc.test.queue.gen.InitMsg;
import org.killbill.billing.rpc.test.queue.gen.TestType;
import org.killbill.bus.DefaultPersistentBus;
import org.killbill.bus.InMemoryPersistentBus;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.bus.dao.PersistentBusSqlDao;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.killbill.commons.jdbi.notification.DatabaseTransactionNotificationApi;
import org.killbill.commons.jdbi.transaction.NotificationTransactionHandler;
import org.killbill.queue.InTransaction;
import org.killbill.queue.QueueObjectMapper;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import static org.killbill.billing.rpc.test.queue.gen.TestType.MEMORY;

public class TestInstance {

    private final Logger logger = LoggerFactory.getLogger(TestInstance.class);

    private final InitMsg initMsg;
    private final InfluxDB influxDB;
    private final BusHandler busHandler;
    private final DatabaseTransactionNotificationApi databaseTransactionNotificationApi;
    private final DBI dbi;
    private final PersistentBus bus;
    private final Clock clock;
    private final MetricRegistry metricRegistry;
    private final AtomicLong nbEvents;
    private final PersistentBusConfig persistentBusConfig;

    public TestInstance(final InitMsg initMsg, final InfluxDB influxDB, final String jdbcConnection, final String dbUsername, final String dbPassword) {
        this.initMsg = initMsg;
        this.influxDB = influxDB;
        this.nbEvents = new AtomicLong(0);
        this.busHandler = new BusHandler(influxDB);
        this.metricRegistry = new MetricRegistry();
        this.clock = new DefaultClock();
        this.databaseTransactionNotificationApi = new DatabaseTransactionNotificationApi();
        this.dbi = setupDBI(jdbcConnection, dbUsername, dbPassword);
        this.persistentBusConfig = setupQueueConfig();
        this.bus = setupPersistentBus();
    }

    public void start() throws EventBusException {
        if (bus != null) {
            bus.start();
            bus.register(busHandler);
        }
    }

    public void stop() throws EventBusException {
        if (bus != null) {
            bus.unregister(busHandler);
            bus.stop();
        }
    }

    public void postEntry(final EventMsg request) throws EventBusException {
        if (bus != null) {
            bus.post(new TestEvent(request));
        }
    }

    public void insertEntryIntoQueue(final EventMsg request) throws EventBusException {
        final PersistentBusSqlDao dao = dbi.onDemand(PersistentBusSqlDao.class);
        final TestEvent entry = new TestEvent(request);

        final String json;
        try {
            json = QueueObjectMapper.get().writeValueAsString(entry);
            // We use the source info to override the creator name
            final BusEventModelDao model = new BusEventModelDao(entry.getSource(), clock.getUTCNow(), TestEvent.class.getName(), json,
                                                                entry.getUserToken(), entry.getSearchKey1(), entry.getSearchKey2());

            dao.insertEntry(model, persistentBusConfig.getTableName());
        } catch (final JsonProcessingException e) {
            throw new EventBusException("Unable to serialize event " + entry);
        }

    }

    private DBI setupDBI(final String jdbcConnection, final String username, final String password) {

        if (initMsg.getType() == TestType.DB) {
            final TestDataSource testDataSource = new TestDataSource(jdbcConnection, username, password);
            final DBI dbi = new DBI(testDataSource.getDataSource());
            InTransaction.setupDBI(dbi);
            dbi.setTransactionHandler(new NotificationTransactionHandler(databaseTransactionNotificationApi));
            return dbi;
        } else {
            return null;
        }
    }

    private void insertNonNullValue(final Map<String, String> config, final String key, final String value) {
        if (Strings.isNullOrEmpty(value)) {
            return;
        }
        config.put(key, value);
    }

    private PersistentBusConfig setupQueueConfig() {

        final Map<String, String> config = new HashMap<>();
        insertNonNullValue(config, "org.killbill.persistent.bus.main.inMemory", String.valueOf(initMsg.getType() == MEMORY));
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
        switch (initMsg.getType()) {
            case MEMORY:
                return new InMemoryPersistentBus(persistentBusConfig);
            case DB:
                return new DefaultPersistentBus(dbi, clock, persistentBusConfig, metricRegistry, databaseTransactionNotificationApi);
            default:
                return null;
        }
    }

    public long incNbEvents() {

        if (influxDB != null) {
            for (String metricsKey : metricRegistry.getCounters().keySet()) {
                final Counter counter = metricRegistry.getCounters().get(metricsKey);
                final String[] parts = metricsKey.split("\\.");
                final String measurement = "bus_events_" + parts[parts.length - 1];

                final Point.Builder pointBuilder = Point.measurement(measurement)
                                                        .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                                                        .addField("value", counter.getCount());
                influxDB.write(pointBuilder.build());

            }
        }

        return nbEvents.incrementAndGet();

    }
}
