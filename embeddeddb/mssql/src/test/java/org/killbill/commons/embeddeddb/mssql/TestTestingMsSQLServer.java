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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertNotNull;

public class TestTestingMsSQLServer {

    protected MsSQLStandaloneDB embeddedDB;

    @BeforeClass(groups = "fast")
    public void setUp(){
        embeddedDB = new MsSQLStandaloneDB("TestDB","PUT_YOUR_SQL_SERVER_USERNAME_HERE", "PUT_YOUR_SQL_SERVER_PASSWORD_HERE");
    }

    @Test(groups = "fast")
    public void testDatabase() throws IOException {
        final String database = "MYDB";
        final String username = "PUT_YOUR_SQL_SERVER_USERNAME_HERE";
        final String password = "PUT_YOUR_SQL_SERVER_PASSWORD_HERE";
        //test database connection with StandAlone DB
        final MsSQLStandaloneDB testTestingMsSQLServer = new MsSQLStandaloneDB(database, username, password);
        assertNotNull(testTestingMsSQLServer.getJdbcConnectionString());
        assertNotNull(testTestingMsSQLServer.getDataSource());
        try (MsSQLStandaloneDB server = new MsSQLStandaloneDB(database, username, password)) {
            assertEquals(server.getUsername(), username);
            assertEquals(server.getDatabaseName(), database);
            assertEquals(server.getJdbcConnectionString().substring(0, 5), "jdbc:");
            assertEquals(server.getPort(), URI.create(server.getJdbcConnectionString().substring(5)).getPort());

            try (Connection connection = DriverManager.getConnection(server.getJdbcConnectionString())) {
                try (Statement statement = connection.createStatement()) {
                    final String table_name = "test_table";
                    statement.execute(String.format("DROP TABLE IF EXISTS %s", table_name));
                    statement.execute(String.format("CREATE TABLE %s (c1 bigint PRIMARY KEY);", table_name ));
                    statement.execute(String.format("INSERT INTO %s (c1) VALUES (1);", table_name));
                    try (ResultSet resultSet = statement.executeQuery(String.format("SELECT count(*) FROM %s", table_name))) {
                        assertTrue(resultSet.next());
                        assertEquals(resultSet.getLong(1), 1L);
                        assertFalse(resultSet.next());
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

    }

    @Test(groups = "fast")
    public void testEmbeddedDB(){
        assertNotNull(embeddedDB);
    }
}
