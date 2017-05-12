/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.killbill.CreatorName;
import org.killbill.TestSetup;
import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.bus.dao.PersistentBusSqlDao;
import org.killbill.queue.api.PersistentQueueConfig.PersistentQueueMode;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.dao.QueueSqlDao;
import org.skife.config.TimeSpan;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestDBBackedQueue extends TestSetup {

    private static final Logger log = LoggerFactory.getLogger(TestDBBackedQueue.class);


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
    @Test(groups = "mysql")
    public void testOnlyInflightQ() {
        final PersistentBusConfig config = createConfig(1, 10, PersistentQueueMode.STICKY_EVENTS);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "onlyInflightQ-bus_event", metricRegistry, databaseTransactionNotificationApi);
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
            //assertEquals(output.getProcessingOwner(), CreatorName.get());
            //assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.IN_PROCESSING);
            assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);

            assertEquals(output.getSearchKey1(), new Long(i));
            assertEquals(output.getSearchKey2(), new Long(1));

            recordIs.add(output.getRecordId());
            final BusEventModelDao historyInput = new BusEventModelDao(output, CreatorName.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
            queue.moveEntryToHistory(historyInput);
        }

        final List<BusEventModelDao> ready = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 1000, CreatorName.get(), "bus_events");
        assertEquals(ready.size(), 0);

        final List<BusEventModelDao> readyHistory = sqlDao.getEntriesFromIds(recordIs, "bus_events_history");
        assertEquals(readyHistory.size(), 100);
        for (int i = 0; i < 100; i++) {
            assertEquals(readyHistory.get(i).getProcessingState(), PersistentQueueEntryLifecycleState.PROCESSED);
            assertEquals(readyHistory.get(i).getProcessingOwner(), CreatorName.get());
        }

        assertEquals(queue.getTotalInflightFetched(), 100L);
        assertEquals(queue.getTotalFetched(), 100L);
        assertEquals(queue.getTotalInflightInsert(), 100L);
        assertEquals(queue.getTotalInsert(), 100L);
    }

    @Test(groups = "slow")
    public void testWithExistingEntriesForDifferentOwners() {

        for (int i = 0; i < 5; i++) {
            final BusEventModelDao input = createEntry(new Long(i), "otherOwner");
            sqlDao.insertEntry(input, "bus_events");
        }

        final PersistentBusConfig config = createConfig(1, 10, PersistentQueueMode.STICKY_EVENTS);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "existingEntriesForDifferentOwners-bus_event", metricRegistry, databaseTransactionNotificationApi);
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
            //assertEquals(output.getProcessingOwner(), CreatorName.get());
            //assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.IN_PROCESSING);
            assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);

            assertEquals(output.getSearchKey1(), new Long(i));
            assertEquals(output.getSearchKey2(), new Long(1));

            recordIs.add(output.getRecordId());
            final BusEventModelDao historyInput = new BusEventModelDao(output, CreatorName.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
            queue.moveEntryToHistory(historyInput);
        }

        assertEquals(queue.getTotalInflightFetched(), 10L);
        assertEquals(queue.getTotalFetched(), 10L);
        assertEquals(queue.getTotalInflightInsert(), 10L);
        assertEquals(queue.getTotalInsert(), 10L);

        final List<BusEventModelDao> remaining = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 10, null, "bus_events");
        assertEquals(remaining.size(), 5);
        for (final BusEventModelDao cur : remaining) {
            sqlDao.removeEntry(cur.getRecordId(), "bus_events");
        }
    }


    @Test(groups = "mysql")
    public void testInflightQWithMultipleEntriesPerTransaction() {
        final PersistentBusConfig config = createConfig(3, 10, PersistentQueueMode.STICKY_EVENTS);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "MultipleEntriesPerTransaction-bus_event", metricRegistry, databaseTransactionNotificationApi);
        queue.initialize();

        final BusEventModelDao input1 = createEntry(new Long(1));
        final BusEventModelDao input2 = createEntry(new Long(2));
        final BusEventModelDao input3 = createEntry(new Long(3));

        sqlDao.inTransaction(new Transaction<Void, QueueSqlDao<BusEventModelDao>>() {
            @Override
            public Void inTransaction(final QueueSqlDao<BusEventModelDao> transactional, final TransactionStatus status) throws Exception {
                queue.insertEntryFromTransaction(transactional, input1);
                queue.insertEntryFromTransaction(transactional, input2);
                queue.insertEntryFromTransaction(transactional, input3);
                return null;
            }
        });

        final List<BusEventModelDao> claimed = queue.getReadyEntries();
        assertEquals(claimed.size(), 3);

        long expectedRecordId = -1;
        for (int i = 0; i < claimed.size(); i++) {
            final BusEventModelDao output = claimed.get(i);
            expectedRecordId = (i == 0) ? output.getRecordId() : (expectedRecordId + 1);
            assertEquals(output.getRecordId(), new Long(expectedRecordId));
            assertEquals(output.getClassName(), String.class.getName());
            assertEquals(output.getEventJson(), "json");
        }
    }


    /**
     * We start with 5 events on disk, a queue capacity of 100, and we fetch elements 7 by 7
     * <p/>
     * <p/>
     * We check the logic that turns the Q open for read as soon as we have read the existing items on disk.
     */
    @Test(groups = "mysql")
    public void testInflightQWithSmallExistingEntriesOnStart() {

        for (int i = 0; i < 5; i++) {
            final BusEventModelDao input = createEntry(new Long(i));
            sqlDao.insertEntry(input, "bus_events");
        }

        final PersistentBusConfig config = createConfig(7, 100, PersistentQueueMode.STICKY_EVENTS);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "inflightQWithSmallExistingEntriesOnStart-bus_event", metricRegistry, databaseTransactionNotificationApi);
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
            if (i <= 1) {
                assertFalse(queue.isQueueOpenForRead());
            } else {
                assertTrue(queue.isQueueOpenForRead());
            }
            assertTrue(queue.isQueueOpenForWrite());

            final List<BusEventModelDao> claimed = queue.getReadyEntries();
            for (int j = 0; j < claimed.size(); j++) {
                final BusEventModelDao output = claimed.get(j);
                expectedRecordId = (i == 0 && j == 0) ? output.getRecordId() : (expectedRecordId + 1);
                assertEquals(output.getRecordId(), new Long(expectedRecordId));
                assertEquals(output.getClassName(), String.class.getName());
                assertEquals(output.getEventJson(), "json");

                // Not true, claimed entries are NOT reread from disk and so their status is the same as we inserted them
                //assertEquals(output.getProcessingOwner(), CreatorName.get());
                //assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.IN_PROCESSING);
                //assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);

                assertEquals(output.getSearchKey1(), new Long(i * 7 + j));
                assertEquals(output.getSearchKey2(), new Long(1));

                recordIs.add(output.getRecordId());
                final BusEventModelDao historyInput = new BusEventModelDao(output, CreatorName.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
                queue.moveEntryToHistory(historyInput);
            }
        }

        final List<BusEventModelDao> ready = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 1000, CreatorName.get(), "bus_events");
        assertEquals(ready.size(), 0);

        final List<BusEventModelDao> readyHistory = sqlDao.getEntriesFromIds(recordIs, "bus_events_history");
        assertEquals(readyHistory.size(), 105);
        for (int i = 0; i < 105; i++) {
            assertEquals(readyHistory.get(i).getProcessingState(), PersistentQueueEntryLifecycleState.PROCESSED);
            assertEquals(readyHistory.get(i).getProcessingOwner(), CreatorName.get());
        }

        assertEquals(queue.getTotalInflightFetched(), 91L);
        assertEquals(queue.getTotalFetched(), 105L);
        assertEquals(queue.getTotalInflightInsert(), 100L);
        assertEquals(queue.getTotalInsert(), 100L);
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

        final PersistentBusConfig config = createConfig(20, 100, PersistentQueueMode.STICKY_EVENTS);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "inflightQWithLargeExistingEntriesOnStart-bus_event", metricRegistry, databaseTransactionNotificationApi);
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

            for (final BusEventModelDao output : claimed) {
                expectedRecordId = (expectedRecordId == -1) ? output.getRecordId() : (expectedRecordId + 1);
                assertEquals(output.getRecordId(), new Long(expectedRecordId));
                assertEquals(output.getClassName(), String.class.getName());
                assertEquals(output.getEventJson(), "json");
                //assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);

                assertEquals(output.getSearchKey1(), new Long(expectedRecordId - 1));
                assertEquals(output.getSearchKey2(), new Long(1));
                recordIs.add(output.getRecordId());

                final BusEventModelDao historyInput = new BusEventModelDao(output, CreatorName.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
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

            for (final BusEventModelDao output : claimed) {
                expectedRecordId = (expectedRecordId + 1);
                assertEquals(output.getRecordId(), new Long(expectedRecordId));
                assertEquals(output.getClassName(), String.class.getName());
                assertEquals(output.getEventJson(), "json");
                //assertEquals(output.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);

                assertEquals(output.getSearchKey1(), new Long(expectedRecordId - 1));
                assertEquals(output.getSearchKey2(), new Long(1));
                recordIs.add(output.getRecordId());

                final BusEventModelDao historyInput = new BusEventModelDao(output, CreatorName.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
                queue.moveEntryToHistory(historyInput);
            }
            claimed = queue.getReadyEntries();
            if (claimed.isEmpty()) {
                break;
            }
        }

        assertEquals(queue.getTotalInflightFetched(), 38L);
        assertEquals(queue.getTotalFetched(), 100L);
        assertEquals(queue.getTotalInflightInsert(), 58L);
        assertEquals(queue.getTotalInsert(), 80L);
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

        final PersistentBusConfig config = createConfig(1, 100, PersistentQueueMode.STICKY_EVENTS);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "smallExistingEntriesOnStartAndOverflowWrite_bus-event", metricRegistry, databaseTransactionNotificationApi);
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

        int i = 0;
        for(;;) {
            if (i <= 5) {
                assertFalse(queue.isQueueOpenForRead());
            } else if (i < 106) {
                assertTrue(queue.isQueueOpenForRead());
            } else {
                assertFalse(queue.isQueueOpenForRead());
            }

            final List<BusEventModelDao> allReady = queue.getReadyEntries();
            for (final BusEventModelDao output : allReady) {
                expectedRecordId = (i == 0) ? output.getRecordId() : (expectedRecordId + 1);

                assertEquals(output.getRecordId(), new Long(expectedRecordId));
                assertEquals(output.getClassName(), String.class.getName());
                assertEquals(output.getEventJson(), "json");

                assertEquals(output.getSearchKey1(), new Long(i));
                assertEquals(output.getSearchKey2(), new Long(1));

                recordIs.add(output.getRecordId());
                final BusEventModelDao historyInput = new BusEventModelDao(output, CreatorName.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
                queue.moveEntryToHistory(historyInput);

                i++;
            }
            if (i == 205) {
                break;
            }
        }

        final List<BusEventModelDao> ready = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 1000, CreatorName.get(), "bus_events");
        assertEquals(ready.size(), 0);

        final List<BusEventModelDao> readyHistory = sqlDao.getEntriesFromIds(recordIs, "bus_events_history");
        assertEquals(readyHistory.size(), 205);
        for (i = 0; i < 205; i++) {
            assertEquals(readyHistory.get(i).getProcessingState(), PersistentQueueEntryLifecycleState.PROCESSED);
            assertEquals(readyHistory.get(i).getProcessingOwner(), CreatorName.get());
        }

        assertEquals(queue.getTotalInflightFetched(), 99L);
        assertEquals(queue.getTotalFetched(), 205L);
        assertEquals(queue.getTotalInflightInsert(), 100L);
        assertEquals(queue.getTotalInsert(), 200L);
    }


    @Test(groups = "slow")
    public void testWithOneReaderOneWriter() throws InterruptedException {

        final PersistentBusConfig config = createConfig(7, 100, PersistentQueueMode.STICKY_EVENTS);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "oneReaderOneWriter-bus_event", metricRegistry, databaseTransactionNotificationApi);
        queue.initialize();


        final Thread writer = new Thread(new WriterRunnable(0, 1000, queue));
        final AtomicLong consumed = new AtomicLong(0);
        final ReaderRunnable readerRunnable = new ReaderRunnable(0, consumed, 1000, queue);
        final Thread reader = new Thread(readerRunnable);

        writer.start();
        while (queue.isQueueOpenForWrite()) {
            try {
                Thread.sleep(10);
            } catch (final InterruptedException e) {
            }
        }
        reader.start();

        try {
            writer.join();
            reader.join();
        } catch (final InterruptedException e) {
            Assert.fail("InterruptedException ", e);
        }

        final List<BusEventModelDao> ready = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 1000, CreatorName.get(), "bus_events");
        assertEquals(ready.size(), 0);

        log.info("Got inflightProcessed = " + queue.getTotalInflightFetched() + "/1000, inflightWritten = " + queue.getTotalInflightInsert() + "/1000");
        assertEquals(queue.getTotalInsert(), 1000L);

        // Verify ordering
        long expected = 999;
        for (final Long cur : readerRunnable.getSearch1()) {
            assertEquals(cur.longValue(), expected);
            expected--;
        }
    }


    @Test(groups = "slow", enabled=true)
    public void testMultipleWritersOneReader() throws InterruptedException {

        final PersistentBusConfig config = createConfig(7, 100, PersistentQueueMode.STICKY_EVENTS);
        queue = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "multipleReaderMultipleWriter-bus_event", metricRegistry, databaseTransactionNotificationApi);
        queue.initialize();


        final Thread[] writers = new Thread[2];
        final Thread[] readers = new Thread[1];

        writers[0] = new Thread(new WriterRunnable(0, 1000, queue));
        writers[1] = new Thread(new WriterRunnable(1, 1000, queue));

        final AtomicLong consumed = new AtomicLong(0);
        readers[0] = new Thread(new ReaderRunnable(0, consumed, 2000, queue));


        writers[0].start();
        writers[1].start();

        while (queue.isQueueOpenForWrite()) {
            try {
                Thread.sleep(10);
            } catch (final InterruptedException e) {
            }
        }
        readers[0].start();

        try {
            writers[0].join();
            writers[1].join();
            readers[0].join();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }

        final List<BusEventModelDao> ready = sqlDao.getReadyEntries(clock.getUTCNow().toDate(), 1000, CreatorName.get(), "bus_events");
        assertEquals(ready.size(), 0);

        log.info("Got inflightProcessed = " + queue.getTotalInflightFetched() + "/1000, inflightWritten = " + queue.getTotalInflightInsert() + "/1000");
        assertEquals(queue.getTotalInsert(), 2000);
    }


    public class ReaderRunnable implements Runnable {

        private final DBBackedQueue<BusEventModelDao> queue;
        private final AtomicLong consumed;
        private final int maxEntries;
        private final List<Long> search1;

        public ReaderRunnable(final int readerId, final AtomicLong consumed, final int maxEntries, final DBBackedQueue<BusEventModelDao> queue) {
            this.queue = queue;
            this.consumed = consumed;
            this.maxEntries = maxEntries;
            this.search1 = new ArrayList<Long>();
        }

        @Override
        public void run() {
            do {
                final List<BusEventModelDao> entries = queue.getReadyEntries();
                if (entries.isEmpty()) {
                    try {
                        //log.info("Reader " + readerId + " sleeping for  10 ms got " + consumed.get());
                        Thread.sleep(10);
                    } catch (final InterruptedException e) {
                    }
                } else {
                    for (final BusEventModelDao cur : entries) {
                        search1.add(cur.getSearchKey1());
                        final BusEventModelDao history = new BusEventModelDao(cur, CreatorName.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
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
                } catch (final InterruptedException e) {
                }
            }
        }
    }


    private BusEventModelDao createEntry(final Long searchKey1, final String owner) {
        final String json = "json";
        return new BusEventModelDao(owner, clock.getUTCNow(), String.class.getName(), json, UUID.randomUUID(), searchKey1, 1L);
    }

    private BusEventModelDao createEntry(final Long searchKey1) {
        return createEntry(searchKey1, CreatorName.get());
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
        };
    }
}
