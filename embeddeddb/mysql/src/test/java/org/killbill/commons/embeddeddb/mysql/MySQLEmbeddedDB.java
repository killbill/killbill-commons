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

package org.killbill.commons.embeddeddb.mysql;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.mariadb.jdbc.MariaDbDataSource;

import com.google.common.base.Preconditions;
import io.airlift.testing.mysql.HackedEmbeddedMySql;
import io.airlift.testing.mysql.HackedTestingMySqlServer;

public class MySQLEmbeddedDB extends EmbeddedDB {

    protected final AtomicBoolean started = new AtomicBoolean(false);

    private final HackedTestingMySqlServer mysqldResource;

    public MySQLEmbeddedDB() {
        // Avoid dashes - MySQL doesn't like them
        this("database" + UUID.randomUUID().toString().substring(0, 8),
             "user" + UUID.randomUUID().toString().substring(0, 8),
             "pass" + UUID.randomUUID().toString().substring(0, 8));
    }

    public MySQLEmbeddedDB(final String databaseName, final String username, final String password) {
        super(databaseName, username, password, null);
        final int port;
        try {
            port = HackedEmbeddedMySql.randomPort();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        mysqldResource = new HackedTestingMySqlServer(username, password, port, databaseName);
        this.jdbcConnectionString = mysqldResource.getJdbcUrl(databaseName);
    }

    @Override
    public DBEngine getDBEngine() {
        return DBEngine.MYSQL;
    }

    @Override
    public void initialize() throws IOException {
    }

    @Override
    public void start() throws IOException, SQLException {
        if (started.get()) {
            throw new IOException("MySQL is already running: " + jdbcConnectionString);
        }

        try {
            startMysql();
        } catch (final Exception e) {
            throw new IOException(e);
        }

        createDataSource();

        refreshTableNames();
    }

    @Override
    public void refreshTableNames() throws IOException {
        final String query = String.format("select table_name from information_schema.tables where table_schema = '%s' and table_type = 'BASE TABLE';", databaseName);
        try {
            executeQuery(query, new ResultSetJob() {
                @Override
                public void work(final ResultSet resultSet) throws SQLException {
                    allTables.clear();
                    while (resultSet.next()) {
                        allTables.add(resultSet.getString(1));
                    }
                }
            });
        } catch (final SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public DataSource getDataSource() throws IOException {
        if (!started.get()) {
            throw new IOException("MySQL is not running");
        }
        return super.getDataSource();
    }

    @Override
    public void stop() throws IOException {
        if (!started.get()) {
            throw new IOException("MySQL is not running");
        }
        super.stop();
        stopMysql();
    }

    @Override
    public String getCmdLineConnectionString() {
        Preconditions.checkState(started.get(), "MySQL isn't running");
        return String.format("mysql -u%s -p%s -P%s -S%s/mysql.sock %s", username, password, mysqldResource.getPort(), mysqldResource.getServer().getServerDirectory(), databaseName);
    }

    protected void createDataSource() throws IOException, SQLException {
        Preconditions.checkState(started.get(), "MySQL isn't running");
        if (useConnectionPooling()) {
            dataSource = createHikariDataSource();
        } else {
            final MariaDbDataSource mariaDBDataSource = new MariaDbDataSource();
            try {
                mariaDBDataSource.setUrl(jdbcConnectionString);
            } catch (final SQLException e) {
                throw new IOException(e);
            }
            mariaDBDataSource.setUser(username);
            mariaDBDataSource.setPassword(password);
            dataSource = mariaDBDataSource;
        }
    }

    private void startMysql() throws Exception {
        mysqldResource.start();
        started.set(true);
        logger.info("MySQL started: " + getCmdLineConnectionString());
    }

    private void stopMysql() throws IOException {
        if (mysqldResource != null) {
            try {
                mysqldResource.close();
            } catch (final NullPointerException npe) {
                logger.warn("Failed to shutdown mysql properly ", npe);
            }

            started.set(false);
            logger.info("MySQL stopped");
        }
    }
}
