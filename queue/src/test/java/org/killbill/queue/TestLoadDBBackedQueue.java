/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.killbill.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.killbill.CreatorName;
import org.killbill.TestSetup;
import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.bus.dao.PersistentBusSqlDao;
import org.killbill.queue.DBBackedQueue.ReadyEntriesWithMetrics;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.skife.config.TimeSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.killbill.queue.api.PersistentQueueConfig.PersistentQueueMode;
import static org.testng.Assert.assertEquals;

public class TestLoadDBBackedQueue extends TestSetup {

    private static final Logger log = LoggerFactory.getLogger(TestLoadDBBackedQueue.class);

    private static final String OWNER = CreatorName.get();

    private DBBackedQueue<BusEventModelDao> queue;
    private PersistentBusSqlDao sqlDao;

    // Need to explicitly disable these as they are somehow picked up on CI with specific JDKs
    @BeforeClass(groups = "load", enabled = false)
    public void beforeClass() throws Exception {
        super.beforeClass();
        sqlDao = getDBI().onDemand(PersistentBusSqlDao.class);
    }

    @BeforeMethod(groups = "load", enabled = false)
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        final List<BusEventModelDao> ready = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 100, null, "bus_events");
        assertEquals(ready.size(), 0);
    }


    @Test(groups = "load", enabled = false)
    public void testPollingLoad() {

        final int NB_EVENTS = 1000;
        final int CLAIMED_EVENTS = 10;

        final PersistentBusConfig config = createConfig(CLAIMED_EVENTS, -1, PersistentQueueMode.POLLING);
        queue = new DBBackedQueueWithPolling<BusEventModelDao>(clock, dbi, PersistentBusSqlDao.class, config, "perf-bus_event", metricRegistry);
        queue.initialize();


        for (int i = 0; i < NB_EVENTS; i++) {
            final BusEventModelDao input = createEntry(new Long(i));
            queue.insertEntry(input);
        }

        log.error("Starting load test");

        final long ini = System.nanoTime();
        long cumlGetReadyEntries = 0;
        long cumlMoveEntriesToHistory = 0;
        for (int i = 0; i < NB_EVENTS / CLAIMED_EVENTS; i++) {

            final long t1 = System.nanoTime();
            final ReadyEntriesWithMetrics<BusEventModelDao> result = queue.getReadyEntries();
            final List<BusEventModelDao> ready = result.getEntries();
            assertEquals(ready.size(), CLAIMED_EVENTS);
            final long t2 = System.nanoTime();
            cumlGetReadyEntries += (t2 - t1);

            final Iterable<BusEventModelDao> processed = ready.stream()
                    .map(input -> new BusEventModelDao(input, CreatorName.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED))
                    .collect(Collectors.toUnmodifiableList());

            final long t3 = System.nanoTime();
            queue.moveEntriesToHistory(processed);
            final long t4 = System.nanoTime();
            cumlMoveEntriesToHistory += (t4 - t3);
        }
        final long fini = System.nanoTime();

        log.error("Load test took " + ((fini - ini) / 1000000) + " ms, getReadyEntry = " +
                (cumlGetReadyEntries / 1000000) + " ms, moveEntriesToHistory = " + (cumlMoveEntriesToHistory / 1000000));
    }


    @Test(groups = "load", enabled = false)
    public void testInflightQLoad() throws InterruptedException {

        final int nbEntries = 10000;
        final PersistentBusConfig config = createConfig(10, nbEntries, PersistentQueueMode.STICKY_EVENTS);
        queue = new DBBackedQueueWithInflightQueue<BusEventModelDao>(clock, dbi, PersistentBusSqlDao.class, config, "multipleReaderMultipleWriter-bus_event", metricRegistry, databaseTransactionNotificationApi);
        queue.initialize();
        for (int i = 0; i < nbEntries; i++) {
            final BusEventModelDao input = createEntry(new Long(i + 5));
            queue.insertEntry(input);
        }


        final int maxThreads = 3;
        final Thread[] readers = new Thread[maxThreads];
        final AtomicLong consumed = new AtomicLong(0);

        for (int i = 0; i < maxThreads; i++) {
            readers[i] = new Thread(new ReaderRunnable(consumed, nbEntries, queue));
        }

        final long ini = System.currentTimeMillis();
        for (int i = 0; i < maxThreads; i++) {
            readers[i].start();
        }

        try {
            for (int i = 0; i < maxThreads; i++) {
                readers[i].join();
            }
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }

        final long fini = System.currentTimeMillis();
        final long elapsed = fini - ini;

        log.info(String.format("Processed %s events in %s msec => rate = %s", nbEntries, elapsed,
                ((double) (nbEntries) / (double) elapsed) * 1000));


        final List<BusEventModelDao> ready = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 1000, OWNER, "bus_events");
        assertEquals(ready.size(), 0);

    }


    public class ReaderRunnable implements Runnable {

        private final DBBackedQueue<BusEventModelDao> queue;
        private final AtomicLong consumed;
        private final int maxEntries;
        private final List<Long> search1;

        public ReaderRunnable(final AtomicLong consumed, final int maxEntries, final DBBackedQueue<BusEventModelDao> queue) {
            this.queue = queue;
            this.consumed = consumed;
            this.maxEntries = maxEntries;
            this.search1 = new ArrayList<Long>();
        }

        @Override
        public void run() {
            do {
                final ReadyEntriesWithMetrics<BusEventModelDao> result = queue.getReadyEntries();
                final List<BusEventModelDao> entries = result.getEntries();
                if (entries.isEmpty()) {
                    try {
                        Thread.sleep(10);
                    } catch (final InterruptedException e) {
                    }
                } else {
                    for (final BusEventModelDao cur : entries) {
                        search1.add(cur.getSearchKey1());
                        final BusEventModelDao history = new BusEventModelDao(cur, OWNER, clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
                        queue.moveEntryToHistory(history);
                    }
                    consumed.getAndAdd(entries.size());
                }
            } while (consumed.get() < maxEntries);
        }
    }


    private BusEventModelDao createEntry(final Long searchKey1, final String owner) {
        final String json = "json";
        return new BusEventModelDao(owner, clock.getUTCNow(), String.class.getName(), json, UUID.randomUUID(), searchKey1, 1L);
    }

    private BusEventModelDao createEntry(final Long searchKey1) {
        return createEntry(searchKey1, OWNER);
    }

    private PersistentBusConfig createConfig(final int claimed, final int qCapacity, final PersistentQueueMode mode) {
        return new PersistentBusConfig() {
            @Override
            public boolean isInMemory() {
                return false;
            }
            @Override
            public int getMaxFailureRetries() {
                return 0;
            }
            @Override
            public int getMinInFlightEntries() {
                return 1;
            }
            @Override
            public int getMaxInFlightEntries() {
                return claimed;
            }
            @Override
            public int getMaxEntriesClaimed() {
                return claimed;
            }
            @Override
            public PersistentQueueMode getPersistentQueueMode() {
                return mode;
            }
            @Override
            public TimeSpan getClaimedTime() {
                return new TimeSpan("5m");
            }
            @Override
            public long getPollingSleepTimeMs() {
                return 100;
            }
            @Override
            public boolean isProcessingOff() {
                return false;
            }
            @Override
            public int geMaxDispatchThreads() {
                return 0;
            }

            @Override
            public int geNbLifecycleDispatchThreads() {
                return 1;
            }

            @Override
            public int geNbLifecycleCompleteThreads() {
                return 1;
            }

            @Override
            public int getEventQueueCapacity() {
                return qCapacity;
            }
            @Override
            public String getTableName() {
                return "bus_events";
            }
            @Override
            public String getHistoryTableName() {
                return "bus_events_history";
            }
            @Override
            public TimeSpan getReapThreshold() {
                return new TimeSpan(5, TimeUnit.MINUTES);
            }
            @Override
            public int getMaxReDispatchCount() {
                return 10;
            }

            @Override
            public TimeSpan getReapSchedule() {
                return new TimeSpan(3, TimeUnit.MINUTES);
            }

            @Override
            public TimeSpan getShutdownTimeout() {
                return new TimeSpan(5, TimeUnit.SECONDS);
            }
        };
    }
}
