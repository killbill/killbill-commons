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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.killbill.CreatorName;
import org.killbill.TestSetup;
import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.bus.dao.PersistentBusSqlDao;
import org.skife.config.TimeSpan;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestDBBackedQueueWithInflightQ extends TestSetup {

    private DBBackedQueueWithInflightQueue<BusEventModelDao> queue;
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
    public void testInflightQWithExistingEntries() {

        final int NB_ENTRIES = 2345;
        final PersistentBusConfig config = createConfig();
        queue = new DBBackedQueueWithInflightQueue<BusEventModelDao>(clock, dbi, PersistentBusSqlDao.class, config, "testInflightQWithExistingEntries", metricRegistry, databaseTransactionNotificationApi);

        // Insert entries prior initialization
        for (int i = 0; i < NB_ENTRIES; i++) {
            final BusEventModelDao input = createEntry(new Long(i + 5));
            sqlDao.insertEntry(input, config.getTableName());
        }

        final long readyEntries = queue.getNbReadyEntries();
        assertEquals(readyEntries, NB_ENTRIES);

        queue.initialize();

        assertEquals(queue.getInflightQSize(), NB_ENTRIES);

    }

    private BusEventModelDao createEntry(final Long searchKey1, final String owner) {
        final String json = "json";
        return new BusEventModelDao(owner, clock.getUTCNow(), String.class.getName(), json, UUID.randomUUID(), searchKey1, 1L);
    }

    private BusEventModelDao createEntry(final Long searchKey1) {
        return createEntry(searchKey1, CreatorName.get());
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
                return PersistentQueueMode.STICKY_EVENTS;
            }
            @Override
            public TimeSpan getClaimedTime() {
                return new TimeSpan("5m");
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
