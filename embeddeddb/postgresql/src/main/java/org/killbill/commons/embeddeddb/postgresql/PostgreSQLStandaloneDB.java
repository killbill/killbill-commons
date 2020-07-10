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

import java.io.IOException;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.killbill.commons.embeddeddb.GenericStandaloneDB;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Delegates to a real PostgreSQL database. This can be used for debugging.
 */
public class PostgreSQLStandaloneDB extends GenericStandaloneDB {

    private final int port;

    public PostgreSQLStandaloneDB(final String databaseName, final String username, final String password) {
        this(databaseName, username, password, "jdbc:postgresql://localhost:5432/" + databaseName);
    }

    public PostgreSQLStandaloneDB(final String databaseName, final String username, final String password, final String jdbcConnectionString) {
        super(databaseName, username, password, jdbcConnectionString);
        this.port = URI.create(jdbcConnectionString.substring(5)).getPort();
    }

    @Override
    public DBEngine getDBEngine() {
        return DBEngine.POSTGRESQL;
    }

    @Override
    public void initialize() throws IOException, SQLException {
        super.initialize();
        dataSource = new PGSimpleDataSource();
        ((PGSimpleDataSource) dataSource).setDatabaseName(databaseName);
        ((PGSimpleDataSource) dataSource).setUser(username);
        ((PGSimpleDataSource) dataSource).setPassword(password);
        ((PGSimpleDataSource) dataSource).setUrl(jdbcConnectionString);
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
    public String getCmdLineConnectionString() {
        return String.format("PGPASSWORD=%s psql -U%s -p%s %s", password, username, port, databaseName);
    }
}
