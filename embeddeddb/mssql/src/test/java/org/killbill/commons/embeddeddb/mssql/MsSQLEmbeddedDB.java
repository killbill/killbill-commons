/*
 * Copyright 2014-2020 Groupon, Inc
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

package org.killbill.commons.embeddeddb.mssql;

import java.io.Closeable;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.killbill.commons.embeddeddb.EmbeddedDB;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

public class MsSQLEmbeddedDB extends EmbeddedDB implements Closeable {

    protected final AtomicBoolean started = new AtomicBoolean(false);

    protected final int port;

    private TestingMsSQLServer testingSqlServer;

    protected MsSQLEmbeddedDB(final String databaseName, final String username) {
        super(databaseName, username, null, null);
        this.port = getPort();
        this.jdbcConnectionString = String.format("jdbc:sqlserver://localhost:%s;databaseName=%s;user=%s", port, databaseName, username);
    }

    public MsSQLEmbeddedDB(final String databaseName, final String username, final String password) {
        super(databaseName, username, password,
              String.format("jdbc:sqlserver://localhost:1433;databaseName=%s;user=%s;password=%s",
                            databaseName,
                            username,
                            password));
        this.port = 1433;
    }

    @Override
    public DBEngine getDBEngine() {
        return DBEngine.MSSQL;
    }

    @Override
    public void initialize() throws IOException, SQLException {

    }

    @Override
    public void start() throws IOException, SQLException {
        if (started.get()) {
            throw new IOException("SQL SERVER is already running: " + jdbcConnectionString);
        }

        startSQLServer();

        createDataSource();

        refreshTableNames();
    }

    @Override
    public void stop() throws IOException {
        if (!started.get()) {
            throw new IOException("SQL Server is not running");
        }
        super.stop();
        stopSQLServer();
    }

    @Override
    public void refreshTableNames() throws IOException {
        final String sql = "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_CATALOG = db_name() AND TABLE_TYPE = 'BASE TABLE'";
        try {
            executeQuery(sql, new ResultSetJob() {
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
            throw new IOException("SQL Server is not running");
        }
        return super.getDataSource();
    }

    protected void createDataSource() throws IOException {
        if (useConnectionPooling()) {
            dataSource = createHikariDataSource();
        } else {
            final SQLServerDataSource sqlServerDataSource = new SQLServerDataSource();
            sqlServerDataSource.setDatabaseName(databaseName);
            sqlServerDataSource.setUser(username);
            sqlServerDataSource.setPassword(password);
            sqlServerDataSource.setURL(jdbcConnectionString);
            dataSource = sqlServerDataSource;
        }
    }

    @Override
    public String getCmdLineConnectionString() {
        return String.format("-P %s -U %s -p %s %s", password, username, port, databaseName);
    }

    private void startSQLServer() throws IOException {
        try {
            this.testingSqlServer = new TestingMsSQLServer(username, port, databaseName);
        } catch (final Exception e) {
            throw new IOException(e);
        }

        started.set(true);
        logger.info("SQL Server started: " + getCmdLineConnectionString());
    }

    private void stopSQLServer() throws IOException {
        if (testingSqlServer != null) {
            testingSqlServer.close();

            started.set(false);
            logger.info("SQL Server stopped: " + getCmdLineConnectionString());
        }
    }

    @Override
    public void close() throws IOException {

    }
}
