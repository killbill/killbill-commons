/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.commons.jdbi.guice;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.killbill.commons.health.api.HealthCheckRegistry;
import org.killbill.commons.jdbi.hikari.KillBillHealthChecker;
import org.killbill.commons.jdbi.hikari.KillBillMetricsTrackerFactory;
import org.killbill.commons.metrics.api.MetricRegistry;
import org.skife.config.TimeSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException;

public class DataSourceProvider implements Provider<DataSource> {

    protected final DaoConfig config;
    protected final String poolName;
    protected final boolean useMariaDB;
    protected final EmbeddedDB embeddedDB;

    private DatabaseType databaseType;
    private String dataSourceClassName;
    private String driverClassName;

    private MetricRegistry metricRegistry;
    private HealthCheckRegistry healthCheckRegistry;

    @VisibleForTesting
    static enum DatabaseType {
        GENERIC, MYSQL, H2, POSTGRESQL
    }

    @Inject
    public DataSourceProvider(final DaoConfig config) {
        this(config, null);
    }

    public DataSourceProvider(final DaoConfig config, final String poolName) {
        this(config, poolName, true);
    }

    public DataSourceProvider(final DaoConfig config, final EmbeddedDB embeddedDB, final String poolName) {
        this(config, embeddedDB, poolName, true);
    }

    public DataSourceProvider(final DaoConfig config, final String poolName, final boolean useMariaDB) {
        this(config, null, poolName, useMariaDB);
    }

    public DataSourceProvider(final DaoConfig config, final EmbeddedDB embeddedDB, final String poolName, final boolean useMariaDB) {
        this.config = config;
        this.poolName = poolName;
        this.useMariaDB = useMariaDB;
        this.embeddedDB = embeddedDB;
        parseJDBCUrl();
    }

    @Inject(optional = true)
    public void setMetricsRegistry(final MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    @Inject(optional = true)
    public void setHealthCheckRegistry(final HealthCheckRegistry healthCheckRegistry) {
        this.healthCheckRegistry = healthCheckRegistry;
    }

    @Override
    public DataSource get() {
        final DataSource dataSource = buildDataSource();
        if (embeddedDB != null) {
            embeddedDB.setDataSource(dataSource);
        }
        return dataSource;
    }

    private DataSource buildDataSource() {
        switch(config.getConnectionPoolingType()) {
            case HIKARICP:
                if (dataSourceClassName != null) {
                    loadDriver();
                }
                return new HikariDataSourceBuilder().buildDataSource();
            case NONE:
                if (embeddedDB != null) {
                    try {
                        embeddedDB.initialize();
                        embeddedDB.start();
                        return embeddedDB.getDataSource();
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    } catch (final SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            default:
                break;
        }

        throw new IllegalArgumentException("DataSource " + config.getConnectionPoolingType() + " unsupported");
    }

    private class HikariDataSourceBuilder {

        private final Logger logger = LoggerFactory.getLogger(HikariDataSourceBuilder.class);

        DataSource buildDataSource() {
            final HikariConfig hikariConfig = new HikariConfig();

            hikariConfig.setUsername(config.getUsername());
            hikariConfig.setPassword(config.getPassword());
            hikariConfig.setMaximumPoolSize(config.getMaxActive());
            hikariConfig.setLeakDetectionThreshold(config.getLeakDetectionThreshold().getMillis());
            hikariConfig.setMinimumIdle(config.getMinIdle());
            hikariConfig.setConnectionTimeout(toMilliSeconds(config.getConnectionTimeout()));
            hikariConfig.setIdleTimeout(toMilliSeconds(config.getIdleMaxAge()));
            // value of 0 indicates no maximum lifetime (infinite lifetime), subject of course to the idleTimeout setting
            hikariConfig.setMaxLifetime(toMilliSeconds(config.getMaxConnectionAge()));
            // TODO config.getIdleConnectionTestPeriod() ?
            // ... no such thing on the HikariCP config.getIdleConnectionTestPeriod()
            final String initSQL = config.getConnectionInitSql();
            if (initSQL != null && !initSQL.isEmpty()) {
                hikariConfig.setConnectionInitSql(initSQL);
            }
            hikariConfig.setInitializationFailTimeout(config.isInitializationFailFast() ? 1 : -1);

            hikariConfig.setTransactionIsolation(config.getTransactionIsolationLevel());

            hikariConfig.setReadOnly(config.isReadOnly());

            hikariConfig.setRegisterMbeans(true);

            if (metricRegistry != null) {
                hikariConfig.setMetricsTrackerFactory(new KillBillMetricsTrackerFactory(metricRegistry));
            }

            if (poolName != null) {
                hikariConfig.setPoolName(poolName);
            }

            hikariConfig.addDataSourceProperty("url", config.getJdbcUrl());
            hikariConfig.addDataSourceProperty("user", config.getUsername());
            hikariConfig.addDataSourceProperty("password", config.getPassword());

            if (DatabaseType.MYSQL.equals(databaseType)) {
                hikariConfig.addDataSourceProperty("cachePrepStmts", config.isPreparedStatementsCacheEnabled());
                hikariConfig.addDataSourceProperty("prepStmtCacheSize", config.getPreparedStatementsCacheSize());
                hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", config.getPreparedStatementsCacheSqlLimit());
                if (Float.valueOf(config.getMySQLServerVersion()).compareTo(Float.valueOf("5.1")) >= 0) {
                    hikariConfig.addDataSourceProperty("useServerPrepStmts", config.isServerSidePreparedStatementsEnabled());
                }
            }

            if (dataSourceClassName != null) {
                hikariConfig.setDataSourceClassName(dataSourceClassName);
            } else {
                // Old-school DriverManager-based JDBC
                hikariConfig.setJdbcUrl(config.getJdbcUrl());
                if (driverClassName != null) {
                    hikariConfig.setDriverClassName(driverClassName);
                }
            }

            try {
                final DataSource hikariDataSource = new HikariDataSource(hikariConfig);
                if (healthCheckRegistry != null) {
                    KillBillHealthChecker.registerHealthChecks(hikariDataSource, hikariConfig, healthCheckRegistry);
                }
                return hikariDataSource;
            } catch (final PoolInitializationException e) {
                // When initializationFailFast=true, log the exception to alert the user (the Guice initialization sequence will continue though)
                logger.error("Unable to initialize the database pool", e);
                throw e;
            }
        }
    }

    static int toSeconds(final TimeSpan timeSpan) {
        return toSeconds(timeSpan.getPeriod(), timeSpan.getUnit());
    }

    static int toSeconds(final long period, final TimeUnit timeUnit) {
        return (int) TimeUnit.SECONDS.convert(period, timeUnit);
    }

    static int toMilliSeconds(final TimeSpan timeSpan) {
        return toMilliSeconds(timeSpan.getPeriod(), timeSpan.getUnit());
    }

    static int toMilliSeconds(final long period, final TimeUnit timeUnit) {
        return (int) TimeUnit.MILLISECONDS.convert(period, timeUnit);
    }

    private void parseJDBCUrl() {
        final URI uri = URI.create(config.getJdbcUrl().substring(5));

        final String schemeLocation;
        if (uri.getPath() != null) {
            schemeLocation = null;
        } else if (uri.getSchemeSpecificPart() != null) {
            final String[] schemeParts = uri.getSchemeSpecificPart().split(":");
            schemeLocation = schemeParts[0];
        } else {
            schemeLocation = null;
        }

        dataSourceClassName = config.getDataSourceClassName();
        driverClassName = config.getDriverClassName();

        if ("mysql".equals(uri.getScheme())) {
            databaseType = DatabaseType.MYSQL;
            if (dataSourceClassName == null) {
                if (useMariaDB) {
                    //dataSourceClassName = "org.mariadb.jdbc.MySQLDataSource";
                    dataSourceClassName = "org.killbill.commons.embeddeddb.mysql.KillBillMariaDbDataSource";
                } else {
                    dataSourceClassName = "com.mysql.jdbc.jdbc2.optional.MysqlDataSource";
                }
            }
            if (driverClassName == null) {
                if (useMariaDB) {
                    driverClassName = "org.mariadb.jdbc.Driver";
                } else {
                    driverClassName = "com.mysql.cj.jdbc.Driver";
                }
            }
        } else if ("h2".equals(uri.getScheme()) && ("mem".equals(schemeLocation) || "file".equals(schemeLocation))) {
            databaseType = DatabaseType.H2;
            if (dataSourceClassName == null) {
                dataSourceClassName = "org.h2.jdbcx.JdbcDataSource";
            }
            if (driverClassName == null) {
                driverClassName = "org.h2.Driver";
            }
        } else if ("postgresql".equals(uri.getScheme())) {
            databaseType = DatabaseType.POSTGRESQL;
            if (dataSourceClassName == null) {
                dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource";
            }
            if (driverClassName == null) {
                driverClassName = "org.postgresql.Driver";
            }
        } else {
            databaseType = DatabaseType.GENERIC;
        }
    }

    private void loadDriver() {
        if (driverClassName != null) {
            try {
                Class.forName(driverClassName).newInstance();
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
