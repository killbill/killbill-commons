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

package org.killbill.bus;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.awaitility.Awaitility;
import org.killbill.TestSetup;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBusConfig;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

public class TestLoadDefaultPersistentBus extends TestSetup {

    private static final Logger log = LoggerFactory.getLogger(TestLoadDefaultPersistentBus.class);

    PersistentBus eventBus;

    @BeforeClass(groups = "load")
    public void beforeClass() throws Exception {
        super.beforeClass();

        final Properties properties = new Properties();
        // See sleep time below in LoadHandler
        properties.setProperty("org.killbill.persistent.bus.main.inflight.claimed", "500");
        properties.setProperty("org.killbill.persistent.bus.external.inMemory", "false");
        properties.setProperty("org.killbill.persistent.bus.main.nbThreads", "200");
        properties.setProperty("org.killbill.persistent.bus.main.queue.capacity", "20000");
        properties.setProperty("org.killbill.persistent.bus.main.sleep", "0");
        properties.setProperty("org.killbill.persistent.bus.main.sticky", "true");
        properties.setProperty("org.killbill.persistent.bus.main.useInflightQ", "true");
        final PersistentBusConfig busConfig = new ConfigurationObjectFactory(properties).buildWithReplacements(PersistentBusConfig.class, ImmutableMap.<String, String>of("instanceName", "main"));

        eventBus = new DefaultPersistentBus(dbi, clock, busConfig, metricRegistry, databaseTransactionNotificationApi);
    }

    @BeforeMethod(groups = "load")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        eventBus.startQueue();
    }

    @AfterMethod(groups = "load")
    public void afterMethod() throws Exception {
        eventBus.stopQueue();
    }

    @Test(groups = "load")
    public void testMultiThreadedLoad() throws Exception {
        final LoadHandler consumer = new LoadHandler();
        eventBus.register(consumer);

        final Long targetEventsPerSecond = 700L;
        final int testDurationMinutes = 2;
        final Long nbEvents = targetEventsPerSecond * testDurationMinutes * 60;

        final Producer producer = new Producer(nbEvents, targetEventsPerSecond);
        try {
            final Thread producerThread = new Thread(producer);
            producerThread.start();
            consumer.waitForCompletion(nbEvents, testDurationMinutes * 60 * 1000);
        } finally {
            producer.stop();
        }
    }

    public static final class LoadHandler {

        private final AtomicLong nbEvents = new AtomicLong(0);
        private final AtomicLong nbEventsForLogging = new AtomicLong(0);
        private final AtomicLong lastLogLineTime = new AtomicLong(System.currentTimeMillis());

        @AllowConcurrentEvents
        @Subscribe
        public void processEvent(final LoadBusEvent event) {
            try {
                // Go to a Ruby plugin, database, etc.
                Thread.sleep(200L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            nbEvents.incrementAndGet();
            nbEventsForLogging.incrementAndGet();

            final long delayMillis = System.currentTimeMillis() - lastLogLineTime.get();
            if (delayMillis > 10000) {
                log.info("Consumer processed {} events in {}s", nbEventsForLogging, delayMillis / 1000.0);
                nbEventsForLogging.set(0);
                lastLogLineTime.set(System.currentTimeMillis());
            }
        }

        public void waitForCompletion(final Long expectedEvents, final long timeoutMs) {
            Awaitility.await()
                      .atMost(timeoutMs, TimeUnit.MILLISECONDS)
                      .until(new Callable<Boolean>() {
                          @Override
                          public Boolean call() throws Exception {
                              return nbEvents.get() == expectedEvents;
                          }
                      });
        }
    }

    private static final class LoadBusEvent implements BusEvent {

        private final String payload;
        private final Long searchKey1;
        private final Long searchKey2;
        private final UUID userToken;

        public LoadBusEvent() {
            // Generate 540 bytes of data
            final StringBuilder payloadBuilder = new StringBuilder();
            for (int i = 0; i < 15; i++) {
                payloadBuilder.append(UUID.randomUUID().toString());
            }
            this.payload = payloadBuilder.toString();
            this.searchKey2 = 12L;
            this.searchKey1 = 42L;
            this.userToken = UUID.randomUUID();
        }

        @JsonCreator
        public LoadBusEvent(@JsonProperty("payload") final String payload,
                            @JsonProperty("searchKey1") final Long searchKey1,
                            @JsonProperty("searchKey2") final Long searchKey2,
                            @JsonProperty("userToken") final UUID userToken) {
            this.payload = payload;
            this.searchKey2 = searchKey2;
            this.searchKey1 = searchKey1;
            this.userToken = userToken;
        }

        // Note! Getter required to have the value serialized to disk
        public String getPayload() {
            return payload;
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

    private final class Producer implements Runnable {

        private final AtomicBoolean isStarted = new AtomicBoolean(true);

        // Total number of events to send
        private final Long nbEvents;
        // Producer speed
        private final Long targetEventsPerSecond;

        public Producer(final Long nbEvents, final Long targetEventsPerSecond) {
            this.nbEvents = nbEvents;
            this.targetEventsPerSecond = targetEventsPerSecond;
        }

        public void stop() {
            isStarted.set(false);
        }

        @Override
        public void run() {
            final int batchLengthSeconds = 10;
            final long eventsPerBatch = batchLengthSeconds * targetEventsPerSecond;

            Long nbEventsSent = 0L;
            while (isStarted.get() && nbEventsSent < nbEvents) {
                final Long t1 = System.currentTimeMillis();
                for (int i = 0; i < eventsPerBatch; i++) {
                    postEvent();
                }
                final Long delayMillis = System.currentTimeMillis() - t1;

                final int maxDelayMillis = batchLengthSeconds * 1000;
                if (delayMillis > maxDelayMillis) {
                    log.warn("Generated {} entries in {}s - producer slow", eventsPerBatch, delayMillis / 1000.0);
                } else {
                    log.info("Generated {} entries in {}s", eventsPerBatch, delayMillis / 1000.0);
                    try {
                        Thread.sleep(maxDelayMillis - delayMillis);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                nbEventsSent += eventsPerBatch;
            }

            log.info("Producer shutting down - {} events sent", nbEventsSent);
        }

        private void postEvent() {
            dbi.inTransaction(new TransactionCallback<Void>() {
                @Override
                public Void inTransaction(final Handle conn, final TransactionStatus status) throws Exception {
                    Assert.assertEquals(conn.select("select now();").size(), 1);

                    final BusEvent event = new LoadBusEvent();
                    eventBus.postFromTransaction(event, conn.getConnection());

                    return null;
                }
            });
        }
    }
}
