package com.ning.billing;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.config.SimplePropertyConfigSource;
import org.skife.jdbi.v2.DBI;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.bus.api.PersistentBusConfig;
import com.ning.billing.bus.dao.BusEventEntry;
import com.ning.billing.commons.embeddeddb.mysql.MySQLEmbeddedDB;
import com.ning.billing.commons.jdbi.argument.DateTimeArgumentFactory;
import com.ning.billing.commons.jdbi.argument.DateTimeZoneArgumentFactory;
import com.ning.billing.commons.jdbi.argument.EnumArgumentFactory;
import com.ning.billing.commons.jdbi.argument.LocalDateArgumentFactory;
import com.ning.billing.commons.jdbi.argument.UUIDArgumentFactory;
import com.ning.billing.commons.jdbi.mapper.LowerToCamelBeanMapperFactory;
import com.ning.billing.commons.jdbi.mapper.UUIDMapper;
import com.ning.billing.notificationq.api.NotificationQueueConfig;
import com.ning.billing.notificationq.dao.NotificationEventEntry;
import com.ning.billing.util.clock.ClockMock;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.InputSupplier;
import com.google.common.io.Resources;

import static org.testng.Assert.assertNotNull;

public class TestSetup {

    private MySQLEmbeddedDB embeddedDB;
    private DBI dbi;
    private PersistentBusConfig persistentBusConfig;
    private NotificationQueueConfig notificationQueueConfig;

    protected ClockMock clock;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {

        loadSystemPropertiesFromClasspath("/queue.properties");

        clock = new ClockMock();

        embeddedDB = new MySQLEmbeddedDB("killbillq", "killbillq", "killbillq");
        embeddedDB.initialize();
        embeddedDB.start();


        final String ddl = toString(Resources.getResource("com/ning/billing/queue/ddl.sql").openStream());
        embeddedDB.executeScript(ddl);

        dbi = new DBI(embeddedDB.getDataSource());
        dbi.registerArgumentFactory(new UUIDArgumentFactory());
        dbi.registerArgumentFactory(new DateTimeZoneArgumentFactory());
        dbi.registerArgumentFactory(new DateTimeArgumentFactory());
        dbi.registerArgumentFactory(new LocalDateArgumentFactory());
        dbi.registerArgumentFactory(new EnumArgumentFactory());
        dbi.registerMapper(new UUIDMapper());
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(BusEventEntry.class));
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(NotificationEventEntry.class));

        final ConfigSource configSource = new SimplePropertyConfigSource(System.getProperties());
        persistentBusConfig = new ConfigurationObjectFactory(configSource).build(PersistentBusConfig.class);

        notificationQueueConfig = new ConfigurationObjectFactory(configSource).build(NotificationQueueConfig.class);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        embeddedDB.cleanupAllTables();
        clock.resetDeltaFromReality();
    }

    @AfterClass(groups = "slow")
    public void afterMethod() throws Exception {
        embeddedDB.stop();
    }


    public static String toString(final InputStream stream) throws IOException {
        final InputSupplier<InputStream> inputSupplier = new InputSupplier<InputStream>() {
            @Override
            public InputStream getInput() throws IOException {
                return stream;
            }
        };
        return CharStreams.toString(CharStreams.newReaderSupplier(inputSupplier, Charsets.UTF_8));
    }

    public DBI getDBI()  {
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



}
