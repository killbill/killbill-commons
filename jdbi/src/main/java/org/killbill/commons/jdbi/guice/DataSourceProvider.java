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

package org.killbill.commons.jdbi.guice;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.skife.config.TimeSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.PoolInitializationException;

public class DataSourceProvider implements Provider<DataSource> {

    protected final DaoConfig config;
    protected final String poolName;
    protected final boolean useMariaDB;
    protected final EmbeddedDB embeddedDB;

    private DatabaseType databaseType;
    private String dataSourceClassName;
    private String driverClassName;

    private Object metricRegistry;
    private Object healthCheckRegistry;

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

    /**
     * Set a Dropwizard MetricRegistry to use for HikariCP.
     *
     * @param metricRegistry the Dropwizard MetricRegistry to set
     */
    @Inject(optional = true)
    public void setMetricRegistry(final MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    /**
     * Set a Dropwizard HealthCheckRegistry to use for HikariCP.
     *
     * @param healthCheckRegistry the Dropwizard HealthCheckRegistry to set
     */
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
            case C3P0:
                loadDriver();
                return new C3P0DataSourceBuilder().buildDataSource();
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
            hikariConfig.setInitializationFailFast(config.isInitializationFailFast());

            hikariConfig.setTransactionIsolation(config.getTransactionIsolationLevel());

            hikariConfig.setReadOnly(config.isReadOnly());

            hikariConfig.setRegisterMbeans(true);

            if (metricRegistry != null) {
                // See https://github.com/brettwooldridge/HikariCP/wiki/Dropwizard-Metrics
                hikariConfig.setMetricRegistry(metricRegistry);
            }
            if (healthCheckRegistry != null) {
                // See https://github.com/brettwooldridge/HikariCP/wiki/Dropwizard-HealthChecks
                hikariConfig.addHealthCheckProperty("connectivityCheckTimeoutMs", String.valueOf(toMilliSeconds(config.getHealthCheckConnectionTimeout())));
                hikariConfig.addHealthCheckProperty("expected99thPercentileMs", String.valueOf(toMilliSeconds(config.getHealthCheckExpected99thPercentile())));
                hikariConfig.setHealthCheckRegistry(healthCheckRegistry);
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
                return new HikariDataSource(hikariConfig);
            } catch (final PoolInitializationException e) {
                // When initializationFailFast=true, log the exception to alert the user (the Guice initialization sequence will continue though)
                logger.error("Unable to initialize the database pool", e);
                throw e;
            }
        }

    }

    private class C3P0DataSourceBuilder {

        DataSource buildDataSource() {
            final ComboPooledDataSource cpds = new ComboPooledDataSource();
            cpds.setJdbcUrl(config.getJdbcUrl());
            cpds.setUser(config.getUsername());
            cpds.setPassword(config.getPassword());
            // http://www.mchange.com/projects/c3p0/#minPoolSize
            // Minimum number of Connections a pool will maintain at any given time.
            cpds.setMinPoolSize(config.getMinIdle());
            // http://www.mchange.com/projects/c3p0/#maxPoolSize
            // Maximum number of Connections a pool will maintain at any given time.
            cpds.setMaxPoolSize(config.getMaxActive());
            // http://www.mchange.com/projects/c3p0/#checkoutTimeout
            // The number of milliseconds a client calling getConnection() will wait for a Connection to be checked-in or
            // acquired when the pool is exhausted. Zero means wait indefinitely. Setting any positive value will cause the getConnection()
            // call to time-out and break with an SQLException after the specified number of milliseconds.
            cpds.setCheckoutTimeout(toMilliSeconds(config.getConnectionTimeout()));
            // http://www.mchange.com/projects/c3p0/#maxIdleTime
            // Seconds a Connection can remain pooled but unused before being discarded. Zero means idle connections never expire.
            cpds.setMaxIdleTime(toSeconds(config.getIdleMaxAge()));
            // http://www.mchange.com/projects/c3p0/#maxConnectionAge
            // Seconds, effectively a time to live. A Connection older than maxConnectionAge will be destroyed and purged from the pool.
            // This differs from maxIdleTime in that it refers to absolute age. Even a Connection which has not been much idle will be purged
            // from the pool if it exceeds maxConnectionAge. Zero means no maximum absolute age is enforced.
            cpds.setMaxConnectionAge(toSeconds(config.getMaxConnectionAge()));
            // http://www.mchange.com/projects/c3p0/#idleConnectionTestPeriod
            // If this is a number greater than 0, c3p0 will test all idle, pooled but unchecked-out connections, every this number of seconds.
            cpds.setIdleConnectionTestPeriod(toSeconds(config.getIdleConnectionTestPeriod()));
            // The number of PreparedStatements c3p0 will cache for a single pooled Connection.
            // If both maxStatements and maxStatementsPerConnection are zero, statement caching will not be enabled.
            // If maxStatementsPerConnection is zero but maxStatements is a non-zero value, statement caching will be enabled,
            // and a global limit enforced, but otherwise no limit will be set on the number of cached statements for a single Connection.
            // If set, maxStatementsPerConnection should be set to about the number distinct PreparedStatements that are used
            // frequently in your application, plus two or three extra so infrequently statements don't force the more common
            // cached statements to be culled. Though maxStatements is the JDBC standard parameter for controlling statement caching,
            // users may find maxStatementsPerConnection more intuitive to use.
            cpds.setMaxStatementsPerConnection(config.getPreparedStatementsCacheSize());
            cpds.setDataSourceName(poolName);

            final String initSQL = config.getConnectionInitSql();
            if (initSQL != null && !initSQL.isEmpty()) {
                final Map<String, Object> extensions = new HashMap<String, Object>(4);
                extensions.put("initSql", initSQL);
                cpds.setExtensions(extensions);
                cpds.setConnectionCustomizerClassName("com.mchange.v2.c3p0.example.InitSqlConnectionCustomizer");
            }
            return cpds;
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
                    driverClassName = "com.mysql.jdbc.Driver";
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
