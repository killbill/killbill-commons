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

package org.killbill.commons.embeddeddb.postgresql;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

// Forked from https://github.com/airlift/testing-postgresql-server (as of 9.6.3-3)
// Added Java 6 support and ability to configure the port
class KillBillTestingPostgreSqlServer implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(KillBillTestingPostgreSqlServer.class);

    private final String user;
    private final String database;
    private final int port;
    private final KillBillEmbeddedPostgreSql server;

    public KillBillTestingPostgreSqlServer(final String user, final String database) throws Exception {
        this(user, null, database);
    }

    public KillBillTestingPostgreSqlServer(final String user, @Nullable final Integer portOrNull, final String database) throws Exception {
        // Make sure the driver is registered
        Class.forName("org.postgresql.Driver");

        this.user = checkNotNull(user, "user is null");
        this.database = checkNotNull(database, "database is null");

        if (portOrNull == null) {
            server = new KillBillEmbeddedPostgreSql();
        } else {
            server = new KillBillEmbeddedPostgreSql(portOrNull);
        }
        port = server.getPort();

        Connection connection = null;
        try {
            connection = server.getPostgresDatabase();
            Statement statement = null;
            try {
                statement = connection.createStatement();
                execute(statement, format("CREATE ROLE %s WITH LOGIN SUPERUSER", user));
                execute(statement, format("CREATE DATABASE %s OWNER %s ENCODING = 'utf8'", database, user));
            } finally {
                if (statement != null) {
                    statement.close();
                }
            }
        } catch (final Exception e) {
            if (connection != null) {
                connection.close();
            }
            server.close();
            throw e;
        }

        log.info("PostgreSQL server ready: {}", getJdbcUrl());
    }

    private static void execute(final Statement statement, final String sql) throws SQLException {
        log.debug("Executing: {}", sql);
        statement.execute(sql);
    }

    @Override
    public void close() throws IOException {
        server.close();
    }

    public String getUser() {
        return user;
    }

    public String getDatabase() {
        return database;
    }

    public int getPort() {
        return port;
    }

    public String getJdbcUrl() {
        return server.getJdbcUrl(user, database);
    }
}
