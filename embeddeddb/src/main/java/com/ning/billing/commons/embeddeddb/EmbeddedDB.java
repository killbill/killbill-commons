/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.commons.embeddeddb;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class EmbeddedDB {

    protected static final Logger logger = LoggerFactory.getLogger(EmbeddedDB.class);

    public enum DBEngine {
        MYSQL,
        H2
    }

    // Not final to allow more flexible implementers
    protected String databaseName;
    protected String username;
    protected String password;
    protected String jdbcConnectionString;

    protected List<String> allTables = new LinkedList<String>();

    protected EmbeddedDB(final String databaseName, final String username, final String password, final String jdbcConnectionString) {
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
        this.jdbcConnectionString = jdbcConnectionString;
    }

    public abstract DBEngine getDBEngine();

    public abstract void initialize() throws IOException;

    public abstract void start() throws IOException;

    public abstract void refreshTableNames() throws IOException;

    public abstract DataSource getDataSource() throws IOException;

    public abstract void stop() throws IOException;

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

    public void executeScript(final String script) throws IOException {
        final String[] rawStatements = script.replaceAll("\n", " ").replaceAll("\r", "").split(";");
        for (final String statement : rawStatements) {
            if (!WHITESPACE_ONLY.matcher(statement).matches()) {
                try {
                    // TODO consider batch approach
                    execute(statement);
                } catch (SQLException e) {
                    throw new IOException(e);
                }

            }
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
            executeQuery("truncate table " + table);
        } catch (SQLException e) {
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

    protected void executeQuery(final String query) throws SQLException, IOException {
        executeQuery(query, new ResultSetJob());
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
}
