/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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

package org.killbill.bus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.common.io.Resources;
import org.killbill.TestSetup;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.commons.embeddeddb.mysql.MySQLEmbeddedDB;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;

public class TestPersistentBusDemo {

    private MySQLEmbeddedDB embeddedDB;
    private DefaultPersistentBus bus;
    private DataSource dataSource;


    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {

        embeddedDB = new MySQLEmbeddedDB("killbillq", "killbillq", "killbillq");
        embeddedDB.initialize();
        embeddedDB.start();

        final String ddl = TestSetup.toString(Resources.getResource("org/killbill/queue/ddl.sql").openStream());
        embeddedDB.executeScript(ddl);

        final String ddlTest = TestSetup.toString(Resources.getResource("queue/ddl_test.sql").openStream());
        embeddedDB.executeScript(ddlTest);

        embeddedDB.refreshTableNames();


        dataSource = embeddedDB.getDataSource();
        final Properties properties = new Properties();
        properties.setProperty("org.killbill.persistent.bus.main.inMemory", "false");
        properties.setProperty("org.killbill.persistent.bus.main.queue.mode", "STICKY_POLLING");
        properties.setProperty("org.killbill.persistent.bus.main.max.failure.retry", "3");
        properties.setProperty("org.killbill.persistent.bus.main.claimed", "100");
        properties.setProperty("org.killbill.persistent.bus.main.claim.time", "5m");
        properties.setProperty("org.killbill.persistent.bus.main.sleep", "100");
        properties.setProperty("org.killbill.persistent.bus.main.off", "false");
        properties.setProperty("org.killbill.persistent.bus.main.nbThreads", "1");
        properties.setProperty("org.killbill.persistent.bus.main.queue.capacity", "3000");
        properties.setProperty("org.killbill.persistent.bus.main.tableName", "bus_events");
        properties.setProperty("org.killbill.persistent.bus.main.historyTableName", "bus_events_history");
        bus = new DefaultPersistentBus(dataSource, properties);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        embeddedDB.cleanupAllTables();
        bus.start();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        bus.stop();
    }

    @Test(groups = "slow")
    public void testDemo() throws SQLException, PersistentBus.EventBusException {

        // Create a Handler (with @Subscribe method)
        final DummyHandler handler = new DummyHandler();
        bus.register(handler);

        // Extract connection from dataSource
        final Connection connection = dataSource.getConnection();
        final DummyEvent event = new DummyEvent("foo", 1L, 2L, UUID.randomUUID());

        PreparedStatement stmt = null;
        try {
            // In one transaction we both insert a dummy value in some table, and post the event (using same connection/transaction)
            connection.setAutoCommit(false);
            stmt = connection.prepareStatement("insert into dummy (dkey, dvalue) values (?, ?)");
            stmt.setString(1, "Great!");
            stmt.setLong(2, 47L);
            stmt.executeUpdate();
            bus.postFromTransaction(event, connection);
            connection.commit();
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (connection != null) {
                connection.close();
            }
        }

        //
        // Verify we see the dummy value inserted and also received the event posted
        //
        final Connection connection2 = dataSource.getConnection();
        PreparedStatement stmt2 = null;
        try {
            stmt2 = connection2.prepareStatement("select * from dummy where dkey = ?");
            stmt2.setString(1, "Great!");
            final ResultSet rs2 = stmt2.executeQuery();
            int found = 0;
            while (rs2.next()) {
                found++;
            }
            Assert.assertEquals(found, 1);
        } finally {
            stmt2.close();
        }
        if (connection2 != null) {
            connection2.close();
        }

        Assert.assertTrue(handler.waitForCompletion(1, 3000));
    }

    public static class DummyEvent implements BusEvent {

        private final String name;
        private final Long searchKey1;
        private final Long searchKey2;
        private final UUID userToken;

        @JsonCreator
        public DummyEvent(@JsonProperty("name") final String name,
                          @JsonProperty("searchKey1") final Long searchKey1,
                          @JsonProperty("searchKey2") final Long searchKey2,
                          @JsonProperty("userToken") final UUID userToken) {
            this.name = name;
            this.searchKey2 = searchKey2;
            this.searchKey1 = searchKey1;
            this.userToken = userToken;
        }

        public String getName() {
            return name;
        }

        @Override
        public Long getSearchKey1() {
            return searchKey1;
        }

        @Override
        public Long getSearchKey2() {
            return searchKey2;
        }

        @Override
        public UUID getUserToken() {
            return userToken;
        }
    }


    public static class DummyHandler {

        private int nbEvents;

        public DummyHandler() {
            nbEvents = 0;
        }

        @AllowConcurrentEvents
        @Subscribe
        public void processEvent(final DummyEvent event) {
            //System.out.println("YEAH!!!!! event = " + event);
            nbEvents++;
        }

        public synchronized boolean waitForCompletion(final int expectedEvents, final long timeoutMs) {

            final long ini = System.currentTimeMillis();
            long remaining = timeoutMs;
            while (nbEvents < expectedEvents && remaining > 0) {
                try {
                    wait(1000);
                    if (nbEvents == expectedEvents) {
                        break;
                    }
                    remaining = timeoutMs - (System.currentTimeMillis() - ini);
                } catch (final InterruptedException ignore) {
                }
            }
            return (nbEvents == expectedEvents);
        }

    }

}
