/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.commons.embeddeddb;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class EmbeddedDB {

    protected static final Logger logger = LoggerFactory.getLogger(EmbeddedDB.class);

    public enum DBEngine {
        GENERIC,
        MYSQL,
        H2,
        POSTGRESQL
    }
    // Not final to allow more flexible implementers
    protected String databaseName;
    protected String username;
    protected String password;
    protected String jdbcConnectionString;
    protected DataSource dataSource;

    protected List<String> allTables = new LinkedList<String>();

    protected EmbeddedDB(final String databaseName, final String username, final String password, final String jdbcConnectionString) {
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
        this.jdbcConnectionString = jdbcConnectionString;
    }

    public boolean useConnectionPooling() {
        return Boolean .getBoolean("killbill.test.use.connection.pool");
    }

    public abstract DBEngine getDBEngine();

    public abstract void initialize() throws IOException;

    public abstract void start() throws IOException;

    public abstract void refreshTableNames() throws IOException;

    public DataSource getDataSource() throws IOException {
        return dataSource;
    }

    // Delayed initialization for GenericStandaloneDB
    public void setDataSource(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void stop() throws IOException {
        final DataSource dataSource = getDataSource();
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).shutdown();
        }
    }

    // Optional - for debugging, how to connect to it?
    public String getCmdLineConnectionString() {
        return null;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getJdbcConnectionString() {
        return jdbcConnectionString;
    }

    public List<String> getAllTables() {
        return allTables;
    }

    private static final Pattern WHITESPACE_ONLY = Pattern.compile("^\\s*$");

    // Only used in tests (embedded versions)
    protected DataSource createHikariDataSource() throws IOException {

        final String dataSourceClassName;
        switch(getDBEngine()) {
            case MYSQL:
                dataSourceClassName = "org.mariadb.jdbc.MySQLDataSource";
                break;
            case POSTGRESQL:
                dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource";
                break;
            case H2:
                dataSourceClassName = "org.h2.jdbcx.JdbcDataSource";
                break;
            default:
                dataSourceClassName = null;
                break;
        }
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
        hikariConfig.setConnectionTimeout(10 * 000);
        hikariConfig.setIdleTimeout(60 * 1000 * 1000);
        hikariConfig.setMaxLifetime(0);


        hikariConfig.setDataSourceClassName(dataSourceClassName);
        return new HikariDataSource(hikariConfig);
    }

    public void executeScript(final String script) throws IOException {
        try {
            execute(script);
        } catch (final SQLException e) {
            throw new IOException(e);
        }
    }

    public void cleanupAllTables() throws IOException {
        for (final String tableName : allTables) {
            cleanupTable(tableName);
        }
    }

    public void cleanupTable(final String table) throws IOException {
        logger.debug("Deleting table: " + table);
        try {
            executeUpdate("truncate table " + table);
        } catch (final SQLException e) {
            throw new IOException(e);
        }
    }

    protected void execute(final String query) throws SQLException, IOException {
        execute(query, new ResultSetJob());
    }

    protected void execute(final String query, final ResultSetJob job) throws SQLException, IOException {
        final Connection connection = getConnection();

        Statement statement = null;
        try {
            statement = connection.createStatement();
            if (statement.execute(query)) {
                job.work(statement.getResultSet());
            }
        } finally {
            if (statement != null) {
                statement.close();
            }
            connection.close();
        }
    }

    protected int executeUpdate(final String query) throws SQLException, IOException {
        final Connection connection = getConnection();

        Statement statement = null;
        try {
            statement = connection.createStatement();
            return statement.executeUpdate(query);
        } finally {
            if (statement != null) {
                statement.close();
            }
            connection.close();
        }
    }

    protected void executeQuery(final String query, final ResultSetJob job) throws SQLException, IOException {
        final Connection connection = getConnection();

        Statement statement = null;
        try {
            statement = connection.createStatement();
            final ResultSet rs = statement.executeQuery(query);
            job.work(rs);
        } finally {
            if (statement != null) {
                statement.close();
            }
            connection.close();
        }
    }

    protected Connection getConnection() throws SQLException, IOException {
        return getDataSource().getConnection();
    }

    protected static class ResultSetJob {

        public void work(final ResultSet resultSet) throws SQLException {}
    }

    protected int getPort() {
        // New socket on any free port
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            return socket.getLocalPort();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (final IOException ignored) {
                }
            }
        }
    }

}
