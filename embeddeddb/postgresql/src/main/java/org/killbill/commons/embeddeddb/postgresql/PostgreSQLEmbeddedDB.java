/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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

package org.killbill.commons.embeddeddb.postgresql;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.postgresql.ds.PGSimpleDataSource;

import io.airlift.testing.postgresql.TestingPostgreSqlServer;

public class PostgreSQLEmbeddedDB extends EmbeddedDB {

    protected final AtomicBoolean started = new AtomicBoolean(false);

    protected PGSimpleDataSource dataSource;
    protected int port;

    private TestingPostgreSqlServer testingPostgreSqlServer;

    public PostgreSQLEmbeddedDB() {
        // Avoid dashes - PostgreSQL doesn't like them
        this("database" + UUID.randomUUID().toString().substring(0, 8),
             "user" + UUID.randomUUID().toString().substring(0, 8));
    }

    public PostgreSQLEmbeddedDB(final String databaseName, final String username) {
        super(databaseName, username, null, null);
    }

    @Override
    public DBEngine getDBEngine() {
        return DBEngine.POSTGRESQL;
    }

    @Override
    public void initialize() throws IOException {
    }

    @Override
    public void start() throws IOException {
        if (started.get()) {
            throw new IOException("PostgreSQL is already running: " + jdbcConnectionString);
        }
        startPostgreSql();

        createDataSource();

        refreshTableNames();
    }

    @Override
    public void refreshTableNames() throws IOException {
        final String query = "select table_name from information_schema.tables where table_schema = current_schema() and table_type = 'BASE TABLE';";
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
            throw new IOException("PostgreSQL is not running");
        }
        return dataSource;
    }

    @Override
    public void stop() throws IOException {
        if (!started.get()) {
            throw new IOException("PostgreSQL is not running");
        }
        stopPostgreSql();
    }

    @Override
    public String getCmdLineConnectionString() {
        return String.format("psql -U%s -p%s %s", username, port, databaseName);
    }

    protected void createDataSource() {
        dataSource = new PGSimpleDataSource();
        dataSource.setDatabaseName(databaseName);
        dataSource.setUser(username);
        dataSource.setUrl(jdbcConnectionString);
    }

    private void startPostgreSql() throws IOException {
        try {
            this.testingPostgreSqlServer = new TestingPostgreSqlServer(username, databaseName);
            this.port = testingPostgreSqlServer.getPort();
            this.jdbcConnectionString = testingPostgreSqlServer.getJdbcUrl();
        } catch (final Exception e) {
            throw new IOException(e);
        }

        started.set(true);
        logger.info("PostgreSQL started: " + getCmdLineConnectionString());
    }

    private void stopPostgreSql() throws IOException {
        if (testingPostgreSqlServer != null) {
            testingPostgreSqlServer.close();

            started.set(false);
            logger.info("PostgreSQL stopped: " + getCmdLineConnectionString());
        }
    }
}
