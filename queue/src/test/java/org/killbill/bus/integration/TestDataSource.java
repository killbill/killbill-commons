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

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class TestDataSource {

    private final String username;
    private final String password;
    private final String jdbcConnectionString;

    public TestDataSource(final String jdbcConnection, final String username, final String password) {
        this.jdbcConnectionString = jdbcConnection;
        this.username = username;
        this.password = password;
    }

    protected DataSource getDataSource() {

        final String dataSourceClassName = "org.mariadb.jdbc.MySQLDataSource";

        loadDriver(dataSourceClassName);

        final HikariConfig hikariConfig = new HikariConfig();

        if (username != null) {
            hikariConfig.setUsername(username);
            hikariConfig.addDataSourceProperty("user", username);
        }
        if (password != null) {
            hikariConfig.setPassword(password);
            hikariConfig.addDataSourceProperty("password", password);
        }
        if (jdbcConnectionString != null) {
            hikariConfig.addDataSourceProperty("url", jdbcConnectionString);
        }

        hikariConfig.setMaximumPoolSize(100);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(10 * 1000);
        hikariConfig.setIdleTimeout(60 * 1000 * 1000);
        hikariConfig.setMaxLifetime(0);

        hikariConfig.setDataSourceClassName(dataSourceClassName);
        return new HikariDataSource(hikariConfig);
    }


    private void loadDriver(final String driverClassName) {
        if (driverClassName != null) {
            try {
                Class.forName(driverClassName).newInstance();
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
