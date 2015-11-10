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

package org.killbill.commons.embeddeddb.mysql;

import java.io.IOException;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.killbill.commons.embeddeddb.GenericStandaloneDB;
import org.mariadb.jdbc.MySQLDataSource;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

/**
 * Delegates to a real MySQL database. This can be used for debugging.
 */
public class MySQLStandaloneDB extends GenericStandaloneDB {

    private final int port;
    private final boolean useMariaDB;

    protected DataSource dataSource;

    public MySQLStandaloneDB(final String databaseName) {
        this(databaseName, "root", null);
    }

    public MySQLStandaloneDB(final String databaseName, final String username, final String password) {
        this(databaseName, username, password, "jdbc:mysql://localhost:3306/" + databaseName + "?createDatabaseIfNotExist=true&allowMultiQueries=true");
    }

    public MySQLStandaloneDB(final String databaseName, final String username, final String password, final String jdbcConnectionString) {
        this(databaseName, username, password, jdbcConnectionString, true);
    }

    public MySQLStandaloneDB(final String databaseName, final String username, final String password, final String jdbcConnectionString, final boolean useMariaDB) {
        super(databaseName, username, password, jdbcConnectionString);
        this.port = URI.create(jdbcConnectionString.substring(5)).getPort();
        this.useMariaDB = useMariaDB;
    }

    @Override
    public DBEngine getDBEngine() {
        return DBEngine.MYSQL;
    }

    @Override
    public void initialize() throws IOException {
        super.initialize();

        if (useMariaDB) {
            final MySQLDataSource mariaDBDataSource = new MySQLDataSource();
            try {
                mariaDBDataSource.setURL(jdbcConnectionString);
            } catch (final SQLException e) {
                throw new IOException(e);
            }
            mariaDBDataSource.setDatabaseName(databaseName);
            mariaDBDataSource.setUser(username);
            mariaDBDataSource.setPassword(password);
            mariaDBDataSource.setPort(port);
            dataSource = mariaDBDataSource;
        } else {
            final MysqlDataSource mysqlDataSource = new MysqlDataSource();
            mysqlDataSource.setDatabaseName(databaseName);
            mysqlDataSource.setUser(username);
            mysqlDataSource.setPassword(password);
            mysqlDataSource.setPort(port);
            // See http://dev.mysql.com/doc/refman/5.0/en/connector-j-reference-configuration-properties.html
            mysqlDataSource.setURL(jdbcConnectionString);
            dataSource = mysqlDataSource;
        }
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
        return dataSource;
    }

    @Override
    public String getCmdLineConnectionString() {
        return String.format("mysql -u%s -p%s -P%s %s", username, password, port, databaseName);
    }
}
