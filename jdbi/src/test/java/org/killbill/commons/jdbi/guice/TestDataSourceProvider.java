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

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcConnectionPool;
import org.killbill.commons.embeddeddb.GenericStandaloneDB;
import org.killbill.commons.embeddeddb.h2.H2EmbeddedDB;
import org.killbill.commons.embeddeddb.mysql.KillBillMariaDbDataSource;
import org.killbill.commons.jdbi.guice.DataSourceProvider.DatabaseType;
import org.mariadb.jdbc.Configuration;
import org.skife.config.ConfigurationObjectFactory;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestDataSourceProvider {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(TestDataSourceProvider.class);

    private static final String TEST_POOL_PREFIX = "test-pool";

    @Test(groups = "fast")
    public void testDataSourceProviderNoPooling() throws Exception {
        DataSourceProvider.DatabaseType databaseType;
        DaoConfig daoConfig;
        String poolName;
        DataSourceProvider dataSourceProvider;

        // H2
        databaseType = DataSourceProvider.DatabaseType.H2;
        daoConfig = buildDaoConfig(DataSourceConnectionPoolingType.NONE, databaseType);

        poolName = TEST_POOL_PREFIX + "-nopool-" + databaseType;
        final H2EmbeddedDB h2EmbeddedDB = new H2EmbeddedDB();
        dataSourceProvider = new DataSourceProvider(daoConfig, h2EmbeddedDB, poolName);

        try {
            assertTrue(dataSourceProvider.get() instanceof JdbcConnectionPool);
        } finally {
            h2EmbeddedDB.stop();
        }

        // Generic
        databaseType = DataSourceProvider.DatabaseType.GENERIC;
        daoConfig = buildDaoConfig(DataSourceConnectionPoolingType.NONE, databaseType);

        poolName = TEST_POOL_PREFIX + "-nopool-" + databaseType;
        final GenericStandaloneDB genericStandaloneDB = new GenericStandaloneDB(null, null, null, null);
        dataSourceProvider = new DataSourceProvider(daoConfig, genericStandaloneDB, poolName);

        assertNull(dataSourceProvider.get());
    }

    @Test(groups = "fast")
    public void testDataSourceProviderHikariCP() throws Exception {
        DataSourceProvider.DatabaseType databaseType;
        DaoConfig daoConfig;
        String poolName;
        DataSourceProvider dataSourceProvider;

        // H2
        databaseType = DataSourceProvider.DatabaseType.H2;
        daoConfig = buildDaoConfig(DataSourceConnectionPoolingType.HIKARICP, databaseType);

        poolName = TEST_POOL_PREFIX + "-0-" + databaseType;
        dataSourceProvider = new DataSourceProvider(daoConfig, poolName);

        assertTrue(dataSourceProvider.get() instanceof HikariDataSource);

        // Generic
        databaseType = DataSourceProvider.DatabaseType.GENERIC;
        daoConfig = buildDaoConfig(DataSourceConnectionPoolingType.HIKARICP, databaseType);

        poolName = TEST_POOL_PREFIX + "-0-" + databaseType;
        dataSourceProvider = new DataSourceProvider(daoConfig, poolName);

        assertTrue(dataSourceProvider.get() instanceof HikariDataSource);
    }

    @Test(groups = "fast")
    public void testDataSourceProviderHikariCPPoolSizing() {
        final DataSourceConnectionPoolingType poolingType = DataSourceConnectionPoolingType.HIKARICP;

        final DataSourceProvider.DatabaseType databaseType = DataSourceProvider.DatabaseType.H2;

        final Properties properties = defaultDaoConfigProperties(poolingType, databaseType);
        properties.put("org.killbill.dao.minIdle", "20");
        properties.put("org.killbill.dao.maxActive", "50");
        final DaoConfig daoConfig = buildDaoConfig(properties);

        final String poolName = TEST_POOL_PREFIX + "-1";
        final DataSource dataSource = new DataSourceProvider(daoConfig, poolName).get();
        assertTrue(dataSource instanceof HikariDataSource);

        final HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        assertEquals(50, hikariDataSource.getMaximumPoolSize());
        assertEquals(20, hikariDataSource.getMinimumIdle());
    }

    @Test(groups = "fast")
    public void testDataSourceProviderHikariCPSetsInitSQL() {
        final DataSourceConnectionPoolingType poolingType = DataSourceConnectionPoolingType.HIKARICP;

        final DataSourceProvider.DatabaseType databaseType = DataSourceProvider.DatabaseType.H2;
        final boolean shouldUseMariaDB = true;

        final Properties properties = defaultDaoConfigProperties(poolingType, databaseType);
        properties.put("org.killbill.dao.connectionInitSql", "SELECT 42");
        final DaoConfig daoConfig = buildDaoConfig(properties);

        final String poolName = TEST_POOL_PREFIX + "-2";
        final DataSource dataSource = new DataSourceProvider(daoConfig, poolName, shouldUseMariaDB).get();
        assertTrue(dataSource instanceof HikariDataSource);

        final HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        assertEquals("SELECT 42", hikariDataSource.getConnectionInitSql());
    }

    @Test(groups = "fast")
    public void testDataSourceProviderHikariCPSetsMariDBDefaults() throws SQLException {
        final DataSourceConnectionPoolingType poolingType = DataSourceConnectionPoolingType.HIKARICP;

        final DataSourceProvider.DatabaseType databaseType = DatabaseType.MYSQL;
        final boolean shouldUseMariaDB = true;

        final Properties properties = defaultDaoConfigProperties(poolingType, databaseType);
        final DaoConfig daoConfig = buildDaoConfig(properties);

        final String poolName = TEST_POOL_PREFIX + "-2";
        final DataSource dataSource = new DataSourceProvider(daoConfig, poolName, shouldUseMariaDB).get();
        assertTrue(dataSource instanceof HikariDataSource);

        final HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        final HikariPool hikariPool = new HikariPool(hikariDataSource);
        final DataSource wrappedDataSource = hikariPool.getUnwrappedDataSource();
        assertTrue(wrappedDataSource instanceof KillBillMariaDbDataSource);

        final KillBillMariaDbDataSource mariaDbDataSource = (KillBillMariaDbDataSource) wrappedDataSource;
        final Configuration configuration = Configuration.parse(mariaDbDataSource.getUrl());
        assertTrue(configuration.cachePrepStmts());
        assertTrue(configuration.useServerPrepStmts());
    }

    @Test(groups = "fast")
    public void testDataSourceProviderC3P0() throws Exception {
        for (final DataSourceProvider.DatabaseType databaseType : DataSourceProvider.DatabaseType.values()) {
            for (final boolean shouldUseMariaDB : new boolean[]{false, true}) {
                final DaoConfig daoConfig = buildDaoConfig(DataSourceConnectionPoolingType.C3P0, databaseType);

                final String poolName = TEST_POOL_PREFIX + "-" + databaseType + "_C3P0";
                final DataSourceProvider dataSourceProvider = new DataSourceProvider(daoConfig, poolName, shouldUseMariaDB);

                final DataSource dataSource = dataSourceProvider.get();
                assertTrue(dataSource instanceof ComboPooledDataSource);
            }
        }
    }

    DaoConfig buildDaoConfig(final DataSourceConnectionPoolingType poolingType, final DataSourceProvider.DatabaseType databaseType) {
        return buildDaoConfig(defaultDaoConfigProperties(poolingType, databaseType));
    }

    DaoConfig buildDaoConfig(final Properties properties) {
        return new ConfigurationObjectFactory(properties).build(DaoConfig.class);
    }

    private Properties defaultDaoConfigProperties(final DataSourceConnectionPoolingType poolingType, final DataSourceProvider.DatabaseType databaseType) {
        final Properties properties = new Properties();
        properties.put("org.killbill.dao.poolingType", poolingType.toString());
        if (DataSourceProvider.DatabaseType.MYSQL.equals(databaseType)) {
            properties.put("org.killbill.dao.url", "jdbc:mysql://127.0.0.1:3306/killbill");
        } else if (DataSourceProvider.DatabaseType.H2.equals(databaseType)) {
            properties.put("org.killbill.dao.url", "jdbc:h2:file:/var/tmp/killbill;MODE=LEGACY;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        } else {
            properties.put("org.killbill.dao.url", "jdbc:test:@myhost:1521:orcl");

            logger.info("GenericDriver.class.getName() = " + GenericDriver.class.getName());

            properties.put("org.killbill.dao.driverClassName", GenericDriver.class.getName());
        }
        return properties;
    }

    public static final class GenericDriver implements Driver {

        static {
            try {
                DriverManager.registerDriver(new GenericDriver());
            } catch (final SQLException e) {
                throw new RuntimeException("Could not register driver", e);
            }
        }

        public Connection connect(final String url, final Properties info) throws SQLException {
            logger.info(this + " connect " + url + " " + info);
            return new ConnectionStub();
        }

        public boolean acceptsURL(final String url) throws SQLException {
            return url.contains(":test:");
        }

        public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties info) throws SQLException {
            return new DriverPropertyInfo[0];
        }

        public int getMajorVersion() {
            return 0;
        }

        public int getMinorVersion() {
            return 0;
        }

        public boolean jdbcCompliant() {
            return false;
        }

        public Logger getParentLogger() {
            return null;
        }

        private class ConnectionStub implements Connection {

            private boolean closed;

            public boolean isValid(final int timeout) throws SQLException {
                return !closed;
            }

            public void close() throws SQLException {
                closed = true;
            }

            public boolean isClosed() throws SQLException {
                return closed;
            }

            public DatabaseMetaData getMetaData() throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public boolean isReadOnly() throws SQLException {
                return true;
            }

            public void setReadOnly(final boolean readOnly) throws SQLException {
            }

            public String getCatalog() throws SQLException {
                return null;
            }

            public void setCatalog(final String catalog) throws SQLException {
            }

            public int getTransactionIsolation() throws SQLException {
                return 0;
            }

            public void setTransactionIsolation(final int level) throws SQLException {
            }

            public Statement createStatement() throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public PreparedStatement prepareStatement(final String sql) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public CallableStatement prepareCall(final String sql) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public String nativeSQL(final String sql) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public boolean getAutoCommit() throws SQLException {
                return false;
            }

            public void setAutoCommit(final boolean autoCommit) throws SQLException {
            }

            public void commit() throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public void rollback() throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public SQLWarning getWarnings() throws SQLException {
                return null;
            }

            public void clearWarnings() throws SQLException {
            }

            public Statement createStatement(final int resultSetType, final int resultSetConcurrency) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public Map<String, Class<?>> getTypeMap() throws SQLException {
                return Collections.emptyMap();
            }

            public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
            }

            public int getHoldability() throws SQLException {
                return 0;
            }

            public void setHoldability(final int holdability) throws SQLException {
            }

            public Savepoint setSavepoint() throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public Savepoint setSavepoint(final String name) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public void rollback(final Savepoint savepoint) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public void releaseSavepoint(final Savepoint savepoint) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public Statement createStatement(final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public Clob createClob() throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public Blob createBlob() throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public NClob createNClob() throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public SQLXML createSQLXML() throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public void setClientInfo(final String name, final String value) throws SQLClientInfoException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public String getClientInfo(final String name) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public Properties getClientInfo() throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public void setClientInfo(final Properties properties) throws SQLClientInfoException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public Array createArrayOf(final String typeName, final Object[] elements) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public Struct createStruct(final String typeName, final Object[] attributes) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public String getSchema() throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public void setSchema(final String schema) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public void abort(final Executor executor) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public void setNetworkTimeout(final Executor executor, final int milliseconds) throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public int getNetworkTimeout() throws SQLException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @SuppressWarnings("unchecked")
            public <T> T unwrap(final Class<T> iface) throws SQLException {
                return (T) this;
            }

            public boolean isWrapperFor(final Class<?> iface) throws SQLException {
                return iface.isInstance(this);
            }
        }
    }
}
