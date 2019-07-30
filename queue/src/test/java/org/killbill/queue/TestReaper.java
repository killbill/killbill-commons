/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.TestSetup;
import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.bus.dao.PersistentBusSqlDao;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.skife.config.TimeSpan;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestReaper extends TestSetup {

    private static final Long SEARCH_KEY_1 = 1L;
    private static final Long SEARCH_KEY_2 = 2L;

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

    @Test(groups = "slow")
    public void testReapEntries() {
        final PersistentBusConfig config = createConfig();
        queue = new DBBackedQueueWithPolling<BusEventModelDao>(clock, dbi, PersistentBusSqlDao.class, config, "testReapEntries", metricRegistry);
        final DateTime now = clock.getUTCNow();

        // Insert ready entry (not yet reapable)
        sqlDao.insertEntry(createEntry(1L, CreatorName.get(), null, now, PersistentQueueEntryLifecycleState.AVAILABLE), config.getTableName());
        // Insert ready entry from another node (not yet reapable)
        sqlDao.insertEntry(createEntry(2L, "thatOtherNode", null, now, PersistentQueueEntryLifecycleState.AVAILABLE), config.getTableName());
        // Insert in-processing entry (not yet reapable)
        sqlDao.insertEntry(createEntry(3L, CreatorName.get(), CreatorName.get(), now, PersistentQueueEntryLifecycleState.IN_PROCESSING), config.getTableName());
        // Insert in-processing entry from another node (not yet reapable)
        sqlDao.insertEntry(createEntry(4L, "thatOtherNode", "thatOtherNode", now, PersistentQueueEntryLifecycleState.IN_PROCESSING), config.getTableName());
        // Insert in-processing entry (reapable)
        sqlDao.insertEntry(createEntryForReaping(5L, CreatorName.get(), CreatorName.get(), now, config.getReapThreshold().getMillis(), PersistentQueueEntryLifecycleState.IN_PROCESSING), config.getTableName());
        // Insert in-processing entry from another node (reapable)
        sqlDao.insertEntry(createEntryForReaping(6L, "thatOtherNode", "thatOtherNode", now, config.getReapThreshold().getMillis(), PersistentQueueEntryLifecycleState.IN_PROCESSING), config.getTableName());

        // Check ready entries
        final List<BusEventModelDao> readyEntries = sqlDao.getReadyEntries(now.toDate(), 10, CreatorName.get(), config.getTableName());
        // One ready entry (STICKY_POLLING mode)
        assertEquals(readyEntries.size(), 1);
        assertEquals(readyEntries.get(0).getRecordId(), (Long) 1L);

        // Check ready and in processing entries
        final List<BusEventModelDao> readyOrInProcessingBeforeReaping = ImmutableList.<BusEventModelDao>copyOf(sqlDao.getReadyOrInProcessingQueueEntriesForSearchKeys(SEARCH_KEY_1, SEARCH_KEY_2, config.getTableName()));
        assertEquals(readyOrInProcessingBeforeReaping.size(), 6);
        assertEquals(readyOrInProcessingBeforeReaping.get(0).getRecordId(), (Long) 1L);
        assertEquals(readyOrInProcessingBeforeReaping.get(1).getRecordId(), (Long) 2L);
        assertEquals(readyOrInProcessingBeforeReaping.get(2).getRecordId(), (Long) 3L);
        assertEquals(readyOrInProcessingBeforeReaping.get(3).getRecordId(), (Long) 4L);
        assertEquals(readyOrInProcessingBeforeReaping.get(4).getRecordId(), (Long) 5L);
        assertEquals(readyOrInProcessingBeforeReaping.get(5).getRecordId(), (Long) 6L);

        // Check history table
        assertFalse(sqlDao.getHistoricalQueueEntriesForSearchKeys(SEARCH_KEY_1, SEARCH_KEY_2, config.getHistoryTableName()).hasNext());

        queue.reapEntries(now.minus(config.getReapThreshold().getMillis()).toDate());

        final List<BusEventModelDao> readyEntriesAfterReaping = sqlDao.getReadyEntries(now.toDate(), 10, CreatorName.get(), config.getTableName());
        assertEquals(readyEntriesAfterReaping.size(), 2);
        assertEquals(readyEntriesAfterReaping.get(0).getRecordId(), (Long) 1L);
        assertTrue(readyEntriesAfterReaping.get(1).getRecordId() > (Long) 6L);

        final List<BusEventModelDao> readyOrInProcessingAfterReaping = ImmutableList.<BusEventModelDao>copyOf(sqlDao.getReadyOrInProcessingQueueEntriesForSearchKeys(SEARCH_KEY_1, SEARCH_KEY_2, config.getTableName()));
        assertEquals(readyOrInProcessingAfterReaping.size(), 6);
        assertEquals(readyOrInProcessingAfterReaping.get(0).getRecordId(), (Long) 1L);
        assertEquals(readyOrInProcessingAfterReaping.get(1).getRecordId(), (Long) 2L);
        assertEquals(readyOrInProcessingAfterReaping.get(2).getRecordId(), (Long) 3L);
        assertEquals(readyOrInProcessingAfterReaping.get(3).getRecordId(), (Long) 4L);
        // That stuck entry hasn't moved (https://github.com/killbill/killbill-commons/issues/47)
        assertEquals(readyOrInProcessingAfterReaping.get(4).getRecordId(), (Long) 5L);
        // New (reaped) one
        assertTrue(readyOrInProcessingAfterReaping.get(5).getRecordId() > (Long) 6L);

        // Check history table
        final List<BusEventModelDao> historicalQueueEntries = ImmutableList.<BusEventModelDao>copyOf(sqlDao.getHistoricalQueueEntriesForSearchKeys(SEARCH_KEY_1, SEARCH_KEY_2, config.getHistoryTableName()));
        assertEquals(historicalQueueEntries.size(), 1);
        assertEquals(historicalQueueEntries.get(0).getProcessingState(), PersistentQueueEntryLifecycleState.REAPED);
        assertEquals(historicalQueueEntries.get(0).getUserToken(), readyOrInProcessingAfterReaping.get(5).getUserToken());
    }

    private BusEventModelDao createEntry(final long recordId,
                                         final String creatingOwner,
                                         final String processingOwner,
                                         final DateTime createdDate,
                                         final PersistentQueueEntryLifecycleState state) {
        return new BusEventModelDao(recordId,
                                    creatingOwner,
                                    processingOwner,
                                    createdDate,
                                    createdDate, // Shouldn't matter
                                    state, String.class.getName(),
                                    "{}",
                                    0L,
                                    UUID.randomUUID(),
                                    SEARCH_KEY_1,
                                    SEARCH_KEY_2);
    }

    private BusEventModelDao createEntryForReaping(final long recordId,
                                                   final String creatingOwner,
                                                   final String processingOwner,
                                                   final DateTime now,
                                                   final long reapThresholdMillis,
                                                   final PersistentQueueEntryLifecycleState state) {
        final DateTime createdDate = now.minus(reapThresholdMillis);
        // For reaping, processingAvailableDate needs to be <= now
        final DateTime processingAvailableDate = now;
        return new BusEventModelDao(recordId,
                                    creatingOwner,
                                    processingOwner,
                                    createdDate,
                                    processingAvailableDate,
                                    state, String.class.getName(),
                                    "{}",
                                    0L,
                                    UUID.randomUUID(),
                                    SEARCH_KEY_1,
                                    SEARCH_KEY_2);
    }

    private PersistentBusConfig createConfig() {
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
                return 100;
            }

            @Override
            public int getMaxEntriesClaimed() {
                return 100;
            }

            @Override
            public PersistentQueueMode getPersistentQueueMode() {
                return PersistentQueueMode.STICKY_POLLING;
            }

            @Override
            public TimeSpan getClaimedTime() {
                return new TimeSpan("1m");
            }

            @Override
            public long getPollingSleepTimeMs() {
                return -1;
            }

            @Override
            public boolean isProcessingOff() {
                return false;
            }

            @Override
            public int geMaxDispatchThreads() {
                return 1;
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
                return -1;
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
        };
    }
}