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
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.influxdb.InfluxDB;
import org.killbill.billing.rpc.test.queue.gen.InitMsg;
import org.killbill.billing.rpc.test.queue.gen.TestStorageType;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.killbill.commons.jdbi.guice.DaoConfig;
import org.killbill.commons.jdbi.guice.DataSourceProvider;
import org.killbill.commons.jdbi.notification.DatabaseTransactionNotificationApi;
import org.killbill.commons.jdbi.transaction.NotificationTransactionHandler;
import org.killbill.queue.InTransaction;
import org.killbill.queue.api.PersistentQueueConfig;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Strings;
import com.zaxxer.hikari.HikariDataSource;

public abstract class TestQueueInstance implements TestInstance {

    private final Logger logger = LoggerFactory.getLogger(TestQueueInstance.class);

    protected final InitMsg initMsg;
    protected final InfluxDB influxDB;
    protected final DatabaseTransactionNotificationApi databaseTransactionNotificationApi;
    protected final DaoConfig daoConfig;
    protected final DBI dbi;
    protected final DataSource dataSource;
    protected final Clock clock;
    protected final MetricRegistry metricRegistry;
    protected final AtomicLong nbEvents;
    protected final PersistentQueueConfig queueConfig;
    protected final JmxReporter reporter;

    public TestQueueInstance(final InitMsg initMsg, final PersistentQueueConfig queueConfig, final InfluxDB influxDB, final String jdbcConnection, final String dbUsername, final String dbPassword) {
        this.initMsg = initMsg;
        this.influxDB = influxDB;
        this.nbEvents = new AtomicLong(0);
        this.metricRegistry = new MetricRegistry();
        this.clock = new DefaultClock();
        this.databaseTransactionNotificationApi = new DatabaseTransactionNotificationApi();
        this.daoConfig = setupDaoConfig(jdbcConnection, dbUsername, dbPassword, initMsg.getMaxPoolConnections());
        this.dataSource = setupDataSource(daoConfig);
        this.dbi = setupDBI(dataSource);
        this.queueConfig = queueConfig;

        reporter = JmxReporter.forRegistry(metricRegistry)
                              .inDomain("org.killbill.queue.integration." + initMsg.getName())
                              .build();
        reporter.start();
    }

    @Override
    public void stop() throws Exception {
        boolean shutdownPool = false;
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
            shutdownPool = true;
        }

        reporter.stop();

        logger.info("Stopped test instance {}, (shutdown pool={})", initMsg.getName(), shutdownPool);
    }

    public long getNbEvents() {
        return nbEvents.get();
    }

    public long incNbEvents() {
        return nbEvents.incrementAndGet();

    }

    protected static void insertNonNullValue(final Map<String, String> config, final String key, final String value) {
        if (Strings.isNullOrEmpty(value)) {
            return;
        }
        config.put(key, value);
    }

    private DataSource setupDataSource(final DaoConfig daoConfig) {
        final DataSourceProvider dataSourceProvider = new DataSourceProvider(daoConfig);
        return dataSourceProvider.get();
    }

    private DBI setupDBI(final DataSource dataSource) {

        if (initMsg.getTestStorageType() == TestStorageType.DB) {
            final DBI dbi = new DBI(dataSource);
            InTransaction.setupDBI(dbi);
            dbi.setTransactionHandler(new NotificationTransactionHandler(databaseTransactionNotificationApi));
            return dbi;
        } else {
            return null;
        }
    }

    private DaoConfig setupDaoConfig(final String jdbcConnection, final String username, final String password, final String maxActive) {

        final Map<String, String> config = new HashMap<>();
        insertNonNullValue(config, "org.killbill.dao.url", jdbcConnection);
        insertNonNullValue(config, "org.killbill.dao.user", username);
        insertNonNullValue(config, "org.killbill.dao.password", password);
        insertNonNullValue(config, "org.killbill.dao.maxActive", maxActive);

        final ConfigSource configSource = new ConfigSource() {
            @Override
            public String getString(final String propertyName) {
                return config.get(propertyName);
            }
        };
        return new ConfigurationObjectFactory(configSource).build(DaoConfig.class);
    }

}
