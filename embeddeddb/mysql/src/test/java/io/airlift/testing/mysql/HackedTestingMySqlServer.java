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

package io.airlift.testing.mysql;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

// Similar to TestingMySqlServer but with the PIERRE notes below
public class HackedTestingMySqlServer implements Closeable {

    // PIERRE: use SLF4J instead
    private static final Logger log = LoggerFactory.getLogger(HackedTestingMySqlServer.class);

    private final String user;
    private final String password;
    private final Set<String> databases;
    private final int port;
    private final String version;
    private final HackedEmbeddedMySql server;

    public HackedTestingMySqlServer(final String user, final String password, final String... databases)
            throws Exception {
        this(user, password, ImmutableList.copyOf(databases));
    }

    public HackedTestingMySqlServer(final String user, final String password, final Iterable<String> databases)
            throws Exception {
        this.user = requireNonNull(user, "user is null");
        this.password = requireNonNull(password, "password is null");
        this.databases = ImmutableSet.copyOf(requireNonNull(databases, "databases is null"));

        server = new HackedEmbeddedMySql();
        port = server.getPort();

        try (final Connection connection = server.getMySqlDatabase()) {
            version = connection.getMetaData().getDatabaseProductVersion();
            try (final Statement statement = connection.createStatement()) {
                // PIERRE: use mysql_native_password
                execute(statement, format("CREATE USER '%s'@'%%' IDENTIFIED WITH mysql_native_password BY '%s'", user, password));
                execute(statement, format("GRANT ALL ON *.* to '%s'@'%%' WITH GRANT OPTION", user));
                for (final String database : databases) {
                    execute(statement, format("CREATE DATABASE %s", database));
                }
            }
        } catch (final SQLException e) {
            close();
            throw e;
        }

        // PIERRE: SLF4J syntax
        log.info("MySQL server ready: {}", getJdbcUrl());
    }

    private static void execute(final Statement statement, final String sql)
            throws SQLException {
        // PIERRE: SLF4J syntax
        log.debug("Executing: {}", sql);
        statement.execute(sql);
    }

    @Override
    public void close() {
        server.close();
    }

    // PIERRE: expose to access the datadir
    public HackedEmbeddedMySql getServer() {
        return server;
    }

    public String getMySqlVersion() {
        return version;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public Set<String> getDatabases() {
        return databases;
    }

    public int getPort() {
        return port;
    }

    public String getJdbcUrl() {
        return getJdbcUrl("");
    }

    // PIERRE: allow multi queries
    public String getJdbcUrl(final String database) {
        return format("jdbc:mysql://localhost:%s/%s?user=%s&password=%s&useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true&allowMultiQueries=true", port, database, user, password);
    }
}