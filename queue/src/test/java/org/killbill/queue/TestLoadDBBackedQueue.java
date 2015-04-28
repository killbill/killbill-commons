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

package org.killbill.queue;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import org.killbill.CreatorName;
import org.killbill.TestSetup;
import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.bus.dao.PersistentBusSqlDao;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.skife.config.TimeSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.assertEquals;

public class TestLoadDBBackedQueue extends TestSetup {

    private final static Logger log = LoggerFactory.getLogger(TestLoadDBBackedQueue.class);

    private final static String OWNER = CreatorName.get();

    private DBBackedQueue<BusEventModelDao> queue;
    private PersistentBusSqlDao sqlDao;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        super.beforeClass();
        sqlDao = getDBI().onDemand(PersistentBusSqlDao.class);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        final List<BusEventModelDao> ready = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 100, null, "bus_events");
        assertEquals(ready.size(), 0);
    }


    @Test(groups = "load")
    public void testPollingLoad() {

        final int NB_EVENTS = 1000;
        final int CLAIMED_EVENTS = 10;

        final PersistentBusConfig config = createConfig(CLAIMED_EVENTS, -1, false, false);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "perf-bus_event", metricRegistry, null);
        queue.initialize();


        for (int i = 0; i < NB_EVENTS; i++) {
            final BusEventModelDao input = createEntry(new Long(i));
            queue.insertEntry(input);
        }

        log.error("Starting load test");

        long ini = System.nanoTime();
        long cumlGetReadyEntries = 0;
        long cumlMoveEntriesToHistory = 0;
        for (int i = 0; i < NB_EVENTS / CLAIMED_EVENTS; i++) {

            long t1 = System.nanoTime();
            final List<BusEventModelDao> ready = queue.getReadyEntries();
            assertEquals(ready.size(), CLAIMED_EVENTS);
            long t2 = System.nanoTime();
            cumlGetReadyEntries += (t2 - t1);

            final Iterable<BusEventModelDao> processed = Iterables.transform(ready, new Function<BusEventModelDao, BusEventModelDao>() {
                @Override
                public BusEventModelDao apply(@Nullable BusEventModelDao input) {
                    return new BusEventModelDao(input, CreatorName.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
                }
            });
            long t3 = System.nanoTime();
            queue.moveEntriesToHistory(processed);
            long t4 = System.nanoTime();
            cumlMoveEntriesToHistory += (t4 - t3);
        }
        long fini = System.nanoTime();

        log.error("Load test took " + ((fini - ini) / 1000000) + " ms, getReadyEntry = " +
                (cumlGetReadyEntries / 1000000) + " ms, moveEntriesToHistory = " + (cumlMoveEntriesToHistory / 1000000));
    }


    @Test(groups = "load")
    public void testInflightQLoad() throws InterruptedException {

        final int nbEntries = 10000;
        final PersistentBusConfig config = createConfig(1, nbEntries, true, true);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "multipleReaderMultipleWriter-bus_event", metricRegistry, databaseTransactionNotificationApi);
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

        long ini = System.currentTimeMillis();
        for (int i = 0; i < maxThreads; i++) {
            readers[i].start();
        }

        try {
            for (int i = 0; i < maxThreads; i++) {
                readers[i].join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long fini = System.currentTimeMillis();
        long elapsed = fini - ini;

        log.info(String.format("Processed %s events in %s msec => rate = %s", nbEntries, elapsed,
                ((double) (nbEntries) / (double) elapsed) * 1000));


        final List<BusEventModelDao> ready = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 1000, OWNER, "bus_events");
        assertEquals(ready.size(), 0);

        log.info("Got inflightProcessed = " + queue.getTotalInflightFetched() + "/1000, inflightWritten = " + queue.getTotalInflightInsert() + "/1000");
        assertEquals(queue.getTotalInsert(), nbEntries);
        assertEquals(queue.getTotalInflightFetched(), nbEntries);
        assertEquals(queue.getTotalInflightInsert(), nbEntries);
    }


    public class ReaderRunnable implements Runnable {

        private final DBBackedQueue<BusEventModelDao> queue;
        private final AtomicLong consumed;
        private final int maxEntries;
        private final List<Long> search1;

        public ReaderRunnable(AtomicLong consumed, final int maxEntries, final DBBackedQueue<BusEventModelDao> queue) {
            this.queue = queue;
            this.consumed = consumed;
            this.maxEntries = maxEntries;
            this.search1 = new ArrayList<Long>();
        }

        @Override
        public void run() {
            do {
                List<BusEventModelDao> entries = queue.getReadyEntries();
                if (entries.size() == 0) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                    }
                } else {
                    for (BusEventModelDao cur : entries) {
                        search1.add(cur.getSearchKey1());
                        final BusEventModelDao history = new BusEventModelDao(cur, OWNER, clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
                        queue.moveEntryToHistory(history);
                    }
                    consumed.getAndAdd(entries.size());
                }
            } while (consumed.get() < maxEntries);
        }
    }


    private BusEventModelDao createEntry(Long searchKey1, String owner) {
        final String json = "json";
        return new BusEventModelDao(owner, clock.getUTCNow(), String.class.getName(), json, UUID.randomUUID(), searchKey1, 1L);
    }

    private BusEventModelDao createEntry(Long searchKey1) {
        return createEntry(searchKey1, OWNER);
    }

    private PersistentBusConfig createConfig(final int claimed, final int qCapacity, final boolean isSticky, final boolean isUsingInflightQ) {
        return new PersistentBusConfig() {
            @Override
            public boolean isInMemory() {
                return false;
            }

            @Override
            public boolean isSticky() {
                return isSticky;
            }

            @Override
            public int getMaxFailureRetries() {
                return 0;
            }

            @Override
            public int getMaxEntriesClaimed() {
                return claimed;
            }

            @Override
            public int getMaxInflightQEntriesClaimed() {
                return claimed;
            }

            @Override
            public TimeSpan getClaimedTime() {
                return new TimeSpan("5m");
            }

            @Override
            public long getSleepTimeMs() {
                return 100;
            }

            @Override
            public boolean isProcessingOff() {
                return false;
            }

            @Override
            public int getNbThreads() {
                return 0;
            }

            @Override
            public boolean isUsingInflightQueue() {
                return isUsingInflightQ;
            }

            @Override
            public int getQueueCapacity() {
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
        };
    }

}
