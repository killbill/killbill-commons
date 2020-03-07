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

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestTestingMsSQLServer {

    @Test(groups = "slow")
    public void testDatabase()
            throws Exception
    {
        try (TestingMsSQLServer server = new TestingMsSQLServer("testuser", "testdb")) {
            assertEquals(server.getUser(), "testuser");
            assertEquals(server.getDatabase(), "testdb");
            assertEquals(server.getJdbcUrl().substring(0, 5), "jdbc:");
            assertEquals(server.getPort(), URI.create(server.getJdbcUrl().substring(5)).getPort());

            try (Connection connection = DriverManager.getConnection(server.getJdbcUrl())) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(String.format("CREATE TABLE %s.test_table (c1 bigint PRIMARY KEY);", server.getDatabase() ));
                    statement.execute(String.format("INSERT INTO %s.test_table (c1) VALUES (1);", server.getDatabase()));
                    try (ResultSet resultSet = statement.executeQuery(String.format("SELECT count(*) FROM %s.test_table", server.getDatabase()))) {
                        assertTrue(resultSet.next());
                        assertEquals(resultSet.getLong(1), 1L);
                        assertFalse(resultSet.next());
                    }
                }
            }
        }
    }
}
