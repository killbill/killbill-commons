/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.commons.jdbi.guice;

import org.killbill.commons.jdbi.log.LogLevel;
import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.DefaultNull;
import org.skife.config.Description;
import org.skife.config.TimeSpan;

public interface DaoConfig {

    @Description("The jdbc url for the database")
    @Config("org.killbill.dao.url")
    @Default("jdbc:h2:file:/var/tmp/killbill;MODE=MYSQL;DB_CLOSE_DELAY=-1;MVCC=true;DB_CLOSE_ON_EXIT=FALSE")
    String getJdbcUrl();

    @Description("The jdbc user name for the database")
    @Config("org.killbill.dao.user")
    @Default("killbill")
    String getUsername();

    @Description("The jdbc password for the database")
    @Config("org.killbill.dao.password")
    @Default("killbill")
    String getPassword();

    @Description("The minimum allowed number of idle connections to the database")
    @Config("org.killbill.dao.minIdle")
    @Default("1")
    int getMinIdle();

    @Description("The maximum allowed number of active connections to the database")
    @Config("org.killbill.dao.maxActive")
    @Default("100")
    int getMaxActive();

    @Description("Amount of time that a connection can be out of the pool before a message is logged indicating a possible connection leak")
    @Config("org.killbill.dao.leakDetectionThreshold")
    @Default("60")
    long getLeakDetectionThreshold();

    @Description("How long to wait before a connection attempt to the database is considered timed out")
    @Config("org.killbill.dao.connectionTimeout")
    @Default("10s")
    TimeSpan getConnectionTimeout();

    @Description("The time for a connection to remain unused before it is closed off")
    @Config("org.killbill.dao.idleMaxAge")
    @Default("60m")
    TimeSpan getIdleMaxAge();

    @Description("Any connections older than this setting will be closed off whether it is idle or not. Connections " +
                 "currently in use will not be affected until they are returned to the pool")
    @Config("org.killbill.dao.maxConnectionAge")
    @Default("0m")
    TimeSpan getMaxConnectionAge();

    @Description("Time for a connection to remain idle before sending a test query to the DB")
    @Config("org.killbill.dao.idleConnectionTestPeriod")
    @Default("5m")
    TimeSpan getIdleConnectionTestPeriod();

    @Description("Sets a SQL statement executed after every new connection creation before adding it to the pool")
    @Config("org.killbill.dao.connectionInitSql")
    @DefaultNull
    String getConnectionInitSql();

    @Description("Number of prepared statements that the driver will cache per connection")
    @Config("org.killbill.dao.prepStmtCacheSize")
    @Default("500")
    int getPreparedStatementsCacheSize();

    @Description("Maximum length of a prepared SQL statement that the driver will cache")
    @Config("org.killbill.dao.prepStmtCacheSqlLimit")
    @Default("2048")
    int getPreparedStatementsCacheSqlLimit();

    @Description("Enable prepared statements cache")
    @Config("org.killbill.dao.cachePrepStmts")
    @Default("true")
    boolean isPreparedStatementsCacheEnabled();

    @Description("Enable server-side prepared statements")
    @Config("org.killbill.dao.useServerPrepStmts")
    @Default("true")
    boolean isServerSidePreparedStatementsEnabled();

    @Description("DataSource class name provided by the JDBC driver, leave null for autodetection")
    @Config("org.killbill.dao.dataSourceClassName")
    @DefaultNull
    String getDataSourceClassName();

    @Description("JDBC driver to use (when dataSourceClassName is null)")
    @Config("org.killbill.dao.driverClassName")
    @DefaultNull
    String getDriverClassName();

    @Description("MySQL server version")
    @Config("org.killbill.dao.mysqlServerVersion")
    @Default("4.0")
    String getMySQLServerVersion();

    @Description("Log level for SQL queries")
    @Config("org.killbill.dao.logLevel")
    @Default("DEBUG")
    LogLevel getLogLevel();

    @Description("Connection pooling type")
    @Config("org.killbill.dao.poolingType")
    @Default("HIKARICP")
    DataSourceConnectionPoolingType getConnectionPoolingType();

    @Description("How long to wait before a connection attempt to the database is considered timed out (healthcheck only)")
    @Config("org.killbill.dao.healthCheckConnectionTimeout")
    @Default("10s")
    TimeSpan getHealthCheckConnectionTimeout();

    @Description("Expected 99th percentile calculation to obtain a connection (healthcheck only)")
    @Config("org.killbill.dao.healthCheckExpected99thPercentile")
    @Default("50ms")
    TimeSpan getHealthCheckExpected99thPercentile();

    @Description("Whether or not initialization should fail on error immediately")
    @Config("org.killbill.dao.initializationFailFast")
    @Default("false")
    boolean isInitializationFailFast();
}
