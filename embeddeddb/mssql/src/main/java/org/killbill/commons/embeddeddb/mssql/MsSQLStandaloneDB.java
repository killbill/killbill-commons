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

import java.io.IOException;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.killbill.commons.embeddeddb.GenericStandaloneDB;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;

public class MsSQLStandaloneDB extends GenericStandaloneDB {

    private final int port;

    public MsSQLStandaloneDB(final String databaseName, final String username, final String password, final String jdbcConnectionString) {
        super(databaseName, username, password, jdbcConnectionString);
        this.port = URI.create(jdbcConnectionString.substring(5)).getPort();
    }

    public MsSQLStandaloneDB(final String databaseName, final String username, final String password) {
        this(databaseName, username, password, "jdbc:sqlserver://localhost:1433;dataBasename=" + databaseName + "; integratedSecurity=true");

    }

    @Override
    public DBEngine getDBEngine() {
        return DBEngine.MSSQL;
    }

    @Override
    public void initialize() throws IOException, SQLException {
        super.initialize();
        dataSource = new SQLServerDataSource();
    }

    @Override
    public void refreshTableNames() throws IOException {
        String sql = "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_CATALOG = db_name() AND TABLE_TYPE = 'BASE TABLE'; GO";
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
    public String getCmdLineConnectionString() {
        return String.format("SQLSERVER_PASSWORD=%s mssql -U%s -p%s %s", password, username, port, databaseName);
    }

    public int getPort(){
        return this.port;
    }
}
