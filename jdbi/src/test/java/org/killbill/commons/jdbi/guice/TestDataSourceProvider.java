/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.skife.config.ConfigurationObjectFactory;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.zaxxer.hikari.HikariDataSource;

public class TestDataSourceProvider {

    private static final String TEST_POOL = "test-pool";

    @Test(groups = "fast")
    public void testDataSourceProviderHikariCP() throws Exception {
        for (final DataSourceProvider.DatabaseType databaseType : DataSourceProvider.DatabaseType.values()) {
            for (final boolean shouldUseMariaDB : new boolean[]{false, true}) {
                final DaoConfig daoConfig = buildDaoConfig(DataSourceConnectionPoolingType.HIKARICP, databaseType);

                final DataSourceProvider dataSourceProvider = new DataSourceProvider(daoConfig, TEST_POOL, shouldUseMariaDB);

                final DataSource dataSource = dataSourceProvider.get();
                assertTrue(dataSource instanceof HikariDataSource);
            }
        }
    }

    @Test(groups = "fast")
    public void testDataSourceProviderHikariCPPoolSizing() {
        final DataSourceConnectionPoolingType poolingType = DataSourceConnectionPoolingType.HIKARICP;

        DataSourceProvider.DatabaseType databaseType = DataSourceProvider.DatabaseType.H2;
        boolean shouldUseMariaDB = true;

        final Properties properties = defaultDaoConfigProperties(poolingType, databaseType);
        properties.put("org.killbill.dao.minIdle", "20");
        properties.put("org.killbill.dao.maxActive", "50");
        final DaoConfig daoConfig = buildDaoConfig(properties);

        final DataSource dataSource = new DataSourceProvider(daoConfig, TEST_POOL, shouldUseMariaDB).get();
        assertTrue(dataSource instanceof HikariDataSource);

        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        assertEquals(50, hikariDataSource.getMaximumPoolSize());
        assertEquals(20, hikariDataSource.getMinimumIdle());
    }

    @Test(groups = "fast")
    public void testDataSourceProviderC3P0() throws Exception {
        for (final DataSourceProvider.DatabaseType databaseType : DataSourceProvider.DatabaseType.values()) {
            for (final boolean shouldUseMariaDB : new boolean[]{false, true}) {
                final DaoConfig daoConfig = buildDaoConfig(DataSourceConnectionPoolingType.C3P0, databaseType);

                final DataSourceProvider dataSourceProvider = new DataSourceProvider(daoConfig, TEST_POOL, shouldUseMariaDB);

                final DataSource dataSource = dataSourceProvider.get();
                assertTrue(dataSource instanceof ComboPooledDataSource);
            }
        }
    }

    DaoConfig buildDaoConfig(DataSourceConnectionPoolingType poolingType, DataSourceProvider.DatabaseType databaseType) {
        return buildDaoConfig( defaultDaoConfigProperties(poolingType, databaseType) );
    }

    DaoConfig buildDaoConfig(final Properties properties) {
        return new ConfigurationObjectFactory(properties).build(DaoConfig.class);
    }

    private Properties defaultDaoConfigProperties(DataSourceConnectionPoolingType poolingType, DataSourceProvider.DatabaseType databaseType) {
        final Properties properties = new Properties();
        properties.put("org.killbill.dao.poolingType", poolingType.toString());
        if (DataSourceProvider.DatabaseType.MYSQL.equals(databaseType)) {
            properties.put("org.killbill.dao.url", "jdbc:mysql://127.0.0.1:3306/killbill");
        } else if (DataSourceProvider.DatabaseType.H2.equals(databaseType)) {
            properties.put("org.killbill.dao.url", "jdbc:h2:file:killbill;MODE=MYSQL;DB_CLOSE_DELAY=-1;MVCC=true;DB_CLOSE_ON_EXIT=FALSE");
        } else {
            properties.put("org.killbill.dao.url", "jdbc:oracle:thin:@myhost:1521:orcl");
            properties.put("org.killbill.dao.driverClassName", GenericDriver.class.getName());
        }
        return properties;
    }

    public static final class GenericDriver implements Driver {

        static {
            try {
                DriverManager.registerDriver(new GenericDriver());
            } catch (SQLException e) {
                throw new RuntimeException("Could not register driver", e);
            }
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return null;
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return url.contains("oracle");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 0;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return null;
        }
    }
}
