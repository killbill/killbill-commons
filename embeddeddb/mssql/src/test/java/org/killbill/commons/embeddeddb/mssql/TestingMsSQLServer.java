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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.killbill.commons.jdbi.notification.DatabaseTransactionNotificationApi;
import org.killbill.commons.jdbi.transaction.NotificationTransactionHandler;
import org.killbill.notificationq.api.NotificationQueueConfig;
import org.killbill.queue.InTransaction;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.config.SimplePropertyConfigSource;
import org.skife.jdbi.v2.DBI;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import junit.extensions.TestSetup;

import static org.testng.Assert.assertNotNull;

public class TestingMsSQLServer implements Closeable {

    protected MsSQLStandaloneDB testDB;

    protected EmbeddedDB embeddedDB;

    protected DBI dbi;
    protected PersistentBusConfig persistentBusConfig;
    protected NotificationQueueConfig notificationQueueConfig;
    //protected ClockMock clock;
    protected final MetricRegistry metricRegistry = new MetricRegistry();
    protected DatabaseTransactionNotificationApi databaseTransactionNotificationApi;

    @BeforeClass(groups = "slow")
    public void setUp(){
        embeddedDB = new MsSQLEmbeddedDB("killbillq","killbillq", "killbillq");
    }

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {

        loadSystemPropertiesFromClasspath("/queue.properties");

        //clock = new ClockMock();

        embeddedDB = new MsSQLEmbeddedDB("killbillq","killbillq", "killbillq");

        embeddedDB.initialize();
        embeddedDB.start();

        final String ddl = toString(Resources.getResource("org/killbill/queue/ddl.sql").openStream());
        embeddedDB.executeScript(ddl);

        embeddedDB.refreshTableNames();


        databaseTransactionNotificationApi = new DatabaseTransactionNotificationApi();
        dbi = new DBI(embeddedDB.getDataSource());
        InTransaction.setupDBI(dbi);
        dbi.setTransactionHandler(new NotificationTransactionHandler(databaseTransactionNotificationApi));

        final ConfigSource configSource = new SimplePropertyConfigSource(System.getProperties());
        persistentBusConfig = new ConfigurationObjectFactory(configSource).buildWithReplacements(PersistentBusConfig.class,
                                                                                                 ImmutableMap.<String, String>of("instanceName", "main"));
        notificationQueueConfig = new ConfigurationObjectFactory(configSource).buildWithReplacements(NotificationQueueConfig.class,
                                                                                                     ImmutableMap.<String, String>of("instanceName", "main"));
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        embeddedDB.cleanupAllTables();
        //clock.resetDeltaFromReality();
        metricRegistry.removeMatching(MetricFilter.ALL);
    }

    @AfterClass(groups = "slow")
    public void afterClass() throws Exception {
        embeddedDB.stop();
    }


    public static String toString(final InputStream inputStream) throws IOException {
        try {
            return new String(ByteStreams.toByteArray(inputStream), Charsets.UTF_8);
        } finally {
            inputStream.close();
        }
    }

    public DBI getDBI() {
        return dbi;
    }

    public PersistentBusConfig getPersistentBusConfig() {
        return persistentBusConfig;
    }

    public NotificationQueueConfig getNotificationQueueConfig() {
        return notificationQueueConfig;
    }

    private static void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = TestSetup.class.getResource(resource);
        assertNotNull(url);
        try {
            System.getProperties().load(url.openStream());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {

    }

    @Test(groups = "fast")
    public void testConnection(){
        //test connection

    }

    @Test(groups = "fast")
    public void testCreateTable(){
        //test creating a table

    }

    @Test(groups = "fast")
    public void testReads(){
        //test reading table

    }

    @Test(groups = "fast")
    public void testPort(){
        Assert.assertNull(testDB);
        //testing connection port
        Assert.assertEquals(1443, testDB.getPort());
    }

    @Test(groups = "fast")
    public void testEmbeddedDB(){
        Assert.assertTrue(embeddedDB instanceof MsSQLEmbeddedDB);
    }
}
