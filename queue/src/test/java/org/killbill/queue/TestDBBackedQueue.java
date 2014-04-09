/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.skife.config.TimeSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.Hostname;
import org.killbill.TestSetup;
import org.killbill.bus.DefaultBusPersistentEvent;
import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.bus.dao.PersistentBusSqlDao;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestDBBackedQueue extends TestSetup {

    private final static Logger log = LoggerFactory.getLogger(TestDBBackedQueue.class);

    private final static String OWNER = Hostname.get();

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

    /**
     * We start with 0 events on disk, a queue capacity of 10.
     * We also read elements one by one.
     * <p/>
     * We will write 100 events, and consume them as we write them.
     */
    @Test(groups = "slow")
    public void testOnlyInflightQ() {
        final PersistentBusConfig config = createConfig(1, 10, false);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "onlyInflightQ-bus_event", metricRegistry);
        queue.initialize();

        long expectedRecordId = -1;
        final List<Long> recordIs = new ArrayList<Long>();
        for (int i = 0; i < 100; i++) {

            assertTrue(queue.isQueueOpenForRead());
            assertTrue(queue.isQueueOpenForWrite());

            final BusEventModelDao input = createEntry(new Long(i));
            queue.insertEntry(input);
            final List<BusEventModelDao> claimed = queue.getReadyEntries();
            assertEquals(claimed.size(), 1);

            final BusEventModelDao output = claimed.get(0);

            expectedRecordId = (i == 0) ? output.getRecordId() : (expectedRecordId + 1);
            assertEquals(output.getRecordId(), new Long(expectedRecordId));
            assertEquals(output.getClassName(), String.class.getName());
            assertEquals(output.getEventJson(), "json");

            // Not true, claimed entries are NOT reread from disk and so their status is the same as we inserted them
            //assertEquals(output.getProcessingOwner(), OWNER);
            //assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.IN_PROCESSING);
            assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);

            assertEquals(output.getSearchKey1(), new Long(i));
            assertEquals(output.getSearchKey2(), new Long(1));

            recordIs.add(output.getRecordId());
            final BusEventModelDao historyInput = new BusEventModelDao(output, OWNER, clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
            queue.moveEntryToHistory(historyInput);
        }

        final List<BusEventModelDao> ready = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 1000, OWNER, "bus_events");
        assertEquals(ready.size(), 0);

        final List<BusEventModelDao> readyHistory = sqlDao.getEntriesFromIds(recordIs, "bus_events_history");
        assertEquals(readyHistory.size(), 100);
        for (int i = 0; i < 100; i++) {
            assertEquals(readyHistory.get(i).getProcessingState(), PersistentQueueEntryLifecycleState.PROCESSED);
            assertEquals(readyHistory.get(i).getProcessingOwner(), OWNER);
        }

        assertEquals(queue.getTotalInflightProcessed(), 100L);
        assertEquals(queue.getTotalProcessed(), 100L);
        assertEquals(queue.getTotalInflightWritten(), 100L);
        assertEquals(queue.getTotalWritten(), 100L);
    }

    @Test(groups = "slow")
    public void testWithExistingEntriesForDifferentOwners() {

        for (int i = 0; i < 5; i++) {
            final BusEventModelDao input = createEntry(new Long(i), "otherOwner");
            sqlDao.insertEntry(input, "bus_events");
        }

        final PersistentBusConfig config = createConfig(1, 10, true);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "existingEntriesForDifferentOwners-bus_event", metricRegistry);
        queue.initialize();

        long expectedRecordId = -1;
        final List<Long> recordIs = new ArrayList<Long>();
        for (int i = 0; i < 10; i++) {

            assertTrue(queue.isQueueOpenForRead());
            assertTrue(queue.isQueueOpenForWrite());

            final BusEventModelDao input = createEntry(new Long(i));
            queue.insertEntry(input);
            final List<BusEventModelDao> claimed = queue.getReadyEntries();
            assertEquals(claimed.size(), 1);

            final BusEventModelDao output = claimed.get(0);

            expectedRecordId = (i == 0) ? output.getRecordId() : (expectedRecordId + 1);
            assertEquals(output.getRecordId(), new Long(expectedRecordId));
            assertEquals(output.getClassName(), String.class.getName());
            assertEquals(output.getEventJson(), "json");

            // Not true, claimed entries are NOT reread from disk and so their status is the same as we inserted them
            //assertEquals(output.getProcessingOwner(), OWNER);
            //assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.IN_PROCESSING);
            assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);

            assertEquals(output.getSearchKey1(), new Long(i));
            assertEquals(output.getSearchKey2(), new Long(1));

            recordIs.add(output.getRecordId());
            final BusEventModelDao historyInput = new BusEventModelDao(output, OWNER, clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
            queue.moveEntryToHistory(historyInput);
        }

        assertEquals(queue.getTotalInflightProcessed(), 10L);
        assertEquals(queue.getTotalProcessed(), 10L);
        assertEquals(queue.getTotalInflightWritten(), 10L);
        assertEquals(queue.getTotalWritten(), 10L);

        final List<BusEventModelDao> remaining = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 10, null, "bus_events");
        assertEquals(remaining.size(), 5);
        for (BusEventModelDao cur : remaining) {
            sqlDao.removeEntry(cur.getRecordId(), "bus_events");
        }
    }

    /**
     * We start with 5 events on disk, a queue capacity of 100, and we fetch elements 7 by 7
     * <p/>
     * <p/>
     * We check the logic that turns the Q open for read as soon as we have read the existing items on disk.
     */
    @Test(groups = "slow")
    public void testInflightQWithSmallExistingEntriesOnStart() {

        for (int i = 0; i < 5; i++) {
            final BusEventModelDao input = createEntry(new Long(i));
            sqlDao.insertEntry(input, "bus_events");
        }

        final PersistentBusConfig config = createConfig(7, 100, false);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "inflightQWithSmallExistingEntriesOnStart-bus_event", metricRegistry);
        queue.initialize();

        assertFalse(queue.isQueueOpenForRead());
        assertTrue(queue.isQueueOpenForWrite());


        final List<Long> recordIs = new ArrayList<Long>();
        for (int i = 5; i < 105; i++) {
            final BusEventModelDao input = createEntry(new Long(i));
            queue.insertEntry(input);
        }

        long expectedRecordId = -1;
        // 15 = 105 /7
        for (int i = 0; i < 15; i++) {
            if (i == 0) {
                assertFalse(queue.isQueueOpenForRead());
            } else {
                assertTrue(queue.isQueueOpenForRead());
            }
            assertTrue(queue.isQueueOpenForWrite());

            List<BusEventModelDao> claimed = queue.getReadyEntries();
            for (int j = 0; j < claimed.size(); j++) {
                final BusEventModelDao output = claimed.get(j);
                expectedRecordId = (i == 0 && j == 0) ? output.getRecordId() : (expectedRecordId + 1);
                assertEquals(output.getRecordId(), new Long(expectedRecordId));
                assertEquals(output.getClassName(), String.class.getName());
                assertEquals(output.getEventJson(), "json");

                // Not true, claimed entries are NOT reread from disk and so their status is the same as we inserted them
                //assertEquals(output.getProcessingOwner(), OWNER);
                //assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.IN_PROCESSING);
                assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);

                assertEquals(output.getSearchKey1(), new Long(i * 7 + j));
                assertEquals(output.getSearchKey2(), new Long(1));

                recordIs.add(output.getRecordId());
                final BusEventModelDao historyInput = new BusEventModelDao(output, OWNER, clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
                queue.moveEntryToHistory(historyInput);
            }
        }

        final List<BusEventModelDao> ready = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 1000, OWNER, "bus_events");
        assertEquals(ready.size(), 0);

        final List<BusEventModelDao> readyHistory = sqlDao.getEntriesFromIds(recordIs, "bus_events_history");
        assertEquals(readyHistory.size(), 105);
        for (int i = 0; i < 105; i++) {
            assertEquals(readyHistory.get(i).getProcessingState(), PersistentQueueEntryLifecycleState.PROCESSED);
            assertEquals(readyHistory.get(i).getProcessingOwner(), OWNER);
        }

        assertEquals(queue.getTotalInflightProcessed(), 98L);
        assertEquals(queue.getTotalProcessed(), 105L);
        assertEquals(queue.getTotalInflightWritten(), 100L);
        assertEquals(queue.getTotalWritten(), 100L);
    }


    /**
     * We start with 20 events on disk, a queue capacity of 100, and we fetch elements 20 by 20
     * <p/>
     * <p/>
     * We check the logic that will first move the Q open for write and then later open for read.
     */
    @Test(groups = "slow")
    public void testInflightQWithLargeExistingEntriesOnStart() {

        for (int i = 0; i < 20; i++) {
            final BusEventModelDao input = createEntry(new Long(i));
            sqlDao.insertEntry(input, "bus_events");
        }

        final PersistentBusConfig config = createConfig(20, 100, false);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "inflightQWithLargeExistingEntriesOnStart-bus_event", metricRegistry);
        queue.initialize();

        assertFalse(queue.isQueueOpenForRead());
        assertFalse(queue.isQueueOpenForWrite());


        final List<Long> recordIs = new ArrayList<Long>();
        for (int i = 20; i < 40; i++) {
            final BusEventModelDao input = createEntry(new Long(i));
            queue.insertEntry(input);
        }


        // At this point we have 40 entries on disk -- not in the inflight queue
        // Iteration 0: get 20, remain 21 (we add one each time)
        // Iteration 1: get 20, remain 1 (we add one each time)
        // Iteration 2: get 1 => break
        long nextCreatedEntry = 40;
        long expectedRecordId = -1;
        List<BusEventModelDao> claimed = queue.getReadyEntries();
        while (true) {

            for (int j = 0; j < claimed.size(); j++) {
                final BusEventModelDao output = claimed.get(j);
                expectedRecordId = (expectedRecordId == -1) ? output.getRecordId() : (expectedRecordId + 1);
                assertEquals(output.getRecordId(), new Long(expectedRecordId));
                assertEquals(output.getClassName(), String.class.getName());
                assertEquals(output.getEventJson(), "json");
                assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);

                assertEquals(output.getSearchKey1(), new Long(expectedRecordId - 1));
                assertEquals(output.getSearchKey2(), new Long(1));
                recordIs.add(output.getRecordId());

                final BusEventModelDao historyInput = new BusEventModelDao(output, OWNER, clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
                queue.moveEntryToHistory(historyInput);
            }
            assertFalse(queue.isQueueOpenForRead());
            if (claimed.size() < 20) {
                break;
            }

            final BusEventModelDao input = createEntry(new Long(nextCreatedEntry++));
            queue.insertEntry(input);
            claimed = queue.getReadyEntries();
        }

        assertTrue(queue.isQueueOpenForWrite());
        assertFalse(queue.isQueueOpenForRead());


        for (int i = 42; i < 100; i++) {
            final BusEventModelDao input = createEntry(new Long(nextCreatedEntry++));
            queue.insertEntry(input);
        }

        claimed = queue.getReadyEntries();
        while (true) {

            for (int j = 0; j < claimed.size(); j++) {
                final BusEventModelDao output = claimed.get(j);
                expectedRecordId = (expectedRecordId + 1);
                assertEquals(output.getRecordId(), new Long(expectedRecordId));
                assertEquals(output.getClassName(), String.class.getName());
                assertEquals(output.getEventJson(), "json");
                assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);

                assertEquals(output.getSearchKey1(), new Long(expectedRecordId - 1));
                assertEquals(output.getSearchKey2(), new Long(1));
                recordIs.add(output.getRecordId());

                final BusEventModelDao historyInput = new BusEventModelDao(output, OWNER, clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
                queue.moveEntryToHistory(historyInput);
            }
            claimed = queue.getReadyEntries();
            if (claimed.size() == 0) {
                break;
            }
        }

        assertEquals(queue.getTotalInflightProcessed(), 38L);
        assertEquals(queue.getTotalProcessed(), 100L);
        assertEquals(queue.getTotalInflightWritten(), 58L);
        assertEquals(queue.getTotalWritten(), 80L);
    }


    /**
     * We start with 5 events on disk, a queue capacity of 100, and we fetch elements 10 by 10
     * We also read elements one by one.
     * <p/>
     * The queue should not be opened for read but should be open for write we start. so we can first overflow the  queue
     * and then verify that we get all entries in the correct order.
     */
    @Test(groups = "slow")
    public void testInflightQWithSmallExistingEntriesOnStartAndOverflowWrite() {

        for (int i = 0; i < 5; i++) {
            final BusEventModelDao input = createEntry(new Long(i));
            sqlDao.insertEntry(input, "bus_events");
        }

        final PersistentBusConfig config = createConfig(1, 100, false);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "smallExistingEntriesOnStartAndOverflowWrite_bus-event", metricRegistry);
        queue.initialize();

        assertFalse(queue.isQueueOpenForRead());
        assertTrue(queue.isQueueOpenForWrite());

        final List<Long> recordIs = new ArrayList<Long>();
        for (int i = 0; i < 200; i++) {

            final BusEventModelDao input = createEntry(new Long(i + 5));
            queue.insertEntry(input);
            if (i >= 100) {
                assertFalse(queue.isQueueOpenForWrite());
            } else {
                assertTrue(queue.isQueueOpenForWrite());
            }
        }

        long expectedRecordId = -1;
        for (int i = 0; i < 205; i++) {
            if (i <= 5) {
                assertFalse(queue.isQueueOpenForRead());
            } else if (i < 106) {
                assertTrue(queue.isQueueOpenForRead());
            } else {
                assertFalse(queue.isQueueOpenForRead());
            }

            List<BusEventModelDao> claimed = queue.getReadyEntries();
            final BusEventModelDao output = claimed.get(0);

            expectedRecordId = (i == 0) ? output.getRecordId() : (expectedRecordId + 1);

            assertEquals(output.getRecordId(), new Long(expectedRecordId));
            assertEquals(output.getClassName(), String.class.getName());
            assertEquals(output.getEventJson(), "json");

            // Not true, claimed entries are NOT reread from disk and so their status is the same as we inserted them
            //assertEquals(output.getProcessingOwner(), OWNER);
            //assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.IN_PROCESSING);
            assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);

            assertEquals(output.getSearchKey1(), new Long(i));
            assertEquals(output.getSearchKey2(), new Long(1));

            recordIs.add(output.getRecordId());
            final BusEventModelDao historyInput = new BusEventModelDao(output, OWNER, clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
            queue.moveEntryToHistory(historyInput);
        }

        final List<BusEventModelDao> ready = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 1000, OWNER, "bus_events");
        assertEquals(ready.size(), 0);

        final List<BusEventModelDao> readyHistory = sqlDao.getEntriesFromIds(recordIs, "bus_events_history");
        assertEquals(readyHistory.size(), 205);
        for (int i = 0; i < 205; i++) {
            assertEquals(readyHistory.get(i).getProcessingState(), PersistentQueueEntryLifecycleState.PROCESSED);
            assertEquals(readyHistory.get(i).getProcessingOwner(), OWNER);
        }

        assertEquals(queue.getTotalInflightProcessed(), 99L);
        assertEquals(queue.getTotalProcessed(), 205L);
        assertEquals(queue.getTotalInflightWritten(), 100L);
        assertEquals(queue.getTotalWritten(), 200L);
    }


    @Test(groups = "slow")
    public void testWithOneReaderOneWriter() throws InterruptedException {

        final PersistentBusConfig config = createConfig(7, 100, false);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "oneReaderOneWriter-bus_event", metricRegistry);
        queue.initialize();


        Thread writer = new Thread(new WriterRunnable(0, 1000, queue));
        final AtomicLong consumed = new AtomicLong(0);
        final ReaderRunnable readerRunnable = new ReaderRunnable(0, consumed, 1000, queue);
        final Thread reader = new Thread(readerRunnable);

        writer.start();
        while (queue.isQueueOpenForWrite()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        }
        reader.start();

        try {
            writer.join();
            reader.join();
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        final List<BusEventModelDao> ready = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 1000, OWNER, "bus_events");
        assertEquals(ready.size(), 0);

        log.info("Got inflightProcessed = " + queue.getTotalInflightProcessed() + "/1000, inflightWritten = " + queue.getTotalInflightWritten() + "/1000");
        assertEquals(queue.getTotalWritten(), 1000L);

        // Verify ordering
        long expected = 999;
        for (Long cur : readerRunnable.getSearch1()) {
            assertEquals(cur.longValue(), expected);
            expected--;
        }
    }


    @Test(groups = "slow")
    public void testMultipleWritersMultipleReaders() throws InterruptedException {

        final PersistentBusConfig config = createConfig(7, 100, false);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "multipleReaderMultipleWriter-bus_event", metricRegistry);
        queue.initialize();


        final Thread[] writers = new Thread[2];
        final Thread[] readers = new Thread[3];

        writers[0] = new Thread(new WriterRunnable(0, 1000, queue));
        writers[1] = new Thread(new WriterRunnable(1, 1000, queue));

        final AtomicLong consumed = new AtomicLong(0);
        readers[0] = new Thread(new ReaderRunnable(0, consumed, 2000, queue));
        readers[1] = new Thread(new ReaderRunnable(1, consumed, 2000, queue));
        readers[2] = new Thread(new ReaderRunnable(2, consumed, 2000, queue));


        writers[0].start();
        writers[1].start();

        while (queue.isQueueOpenForWrite()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        }
        readers[0].start();
        readers[1].start();
        readers[2].start();

        try {
            writers[0].join();
            writers[1].join();
            readers[0].join();
            readers[1].join();
            readers[2].join();
        } catch (InterruptedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        final List<BusEventModelDao> ready = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 1000, OWNER, "bus_events");
        assertEquals(ready.size(), 0);

        log.info("Got inflightProcessed = " + queue.getTotalInflightProcessed() + "/1000, inflightWritten = " + queue.getTotalInflightWritten() + "/1000");
        assertEquals(queue.getTotalWritten(), 2000);
    }

    public class ReaderRunnable implements Runnable {

        private final int readerId;
        private final DBBackedQueue<BusEventModelDao> queue;
        private final AtomicLong consumed;
        private final int maxEntries;
        private final List<Long> search1;

        public ReaderRunnable(final int readerId, AtomicLong consumed, final int maxEntries, final DBBackedQueue<BusEventModelDao> queue) {
            this.readerId = readerId;
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
                        //log.info("Reader " + readerId + " sleeping for  10 ms got " + consumed.get());
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

        public List<Long> getSearch1() {
            return search1;
        }
    }

    public class WriterRunnable implements Runnable {

        private final int writerId;
        private final int nbToWrite;
        private final DBBackedQueue<BusEventModelDao> queue;
        private final Random r;

        public WriterRunnable(final int writerId, final int nbToWrite, final DBBackedQueue<BusEventModelDao> queue) {
            this.writerId = writerId;
            this.nbToWrite = nbToWrite;
            this.queue = queue;
            r = new Random(writerId);
        }

        @Override
        public void run() {
            int remaining = nbToWrite;
            do {
                final long search1 = (nbToWrite * writerId) + (remaining - 1);
                final BusEventModelDao entry = createEntry(new Long(search1));
                queue.insertEntry(entry);
                maybeSleep();
                remaining--;
            } while (remaining > 0);
        }

        private void maybeSleep() {
            while (!queue.isQueueOpenForWrite()) {
                try {
                    //log.info("Writer " + writerId + "sleeping for until queue becomes open for write");
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            }
        }
    }


    private BusEventModelDao createEntry(Long searchKey1, String owner) {
        final String json = "json";
        return new BusEventModelDao(owner, clock.getUTCNow(), String.class.getName(), json, UUID.randomUUID(), searchKey1, 1L);
    }

    private BusEventModelDao createEntry(Long searchKey1) {
        return createEntry(searchKey1, OWNER);
    }

    private PersistentBusConfig createConfig(final int claimed, final int qCapacity, final boolean isSticky) {
        return new PersistentBusConfig() {
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
                return true;
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


    public static class MyEvent extends DefaultBusPersistentEvent {

        private final String name;
        private final Long value;
        private final String type;

        @JsonCreator
        public MyEvent(@JsonProperty("name") final String name,
                       @JsonProperty("value") final Long value,
                       @JsonProperty("token") final UUID token,
                       @JsonProperty("type") final String type,
                       @JsonProperty("searchKey1") final Long searchKey1,
                       @JsonProperty("searchKey2") final Long searchKey2) {
            super(token, searchKey1, searchKey2);
            this.name = name;
            this.value = value;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public Long getValue() {
            return value;
        }

        public String getType() {
            return type;
        }
    }
}
