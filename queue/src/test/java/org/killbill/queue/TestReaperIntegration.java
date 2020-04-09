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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.TestSetup;
import org.killbill.bus.DefaultPersistentBus;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.bus.dao.PersistentBusSqlDao;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.skife.config.TimeSpan;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class TestReaperIntegration extends TestSetup {

    private PersistentBusConfig config;
    private DefaultPersistentBus bus;
    private PersistentBusSqlDao sqlDao;
    private DummyHandler handler;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        super.beforeClass();
        sqlDao = getDBI().onDemand(PersistentBusSqlDao.class);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();

        config = createConfig();
        bus = new DefaultPersistentBus(dbi, clock, config, metricRegistry, databaseTransactionNotificationApi);
        bus.startQueue();

        handler = new DummyHandler();
        bus.register(handler);
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        bus.stopQueue();
    }

    @Test(groups = "slow")
    public void testWithStuckEntryProcessedByAnotherNode() throws EventBusException, JsonProcessingException, InterruptedException {
        final DateTime now = clock.getUTCNow();

        // Verify bus is working
        final BusEvent event1 = new DummyEvent();
        bus.post(event1);
        handler.waitFor(event1);
        handler.assertSeenEvents(1);

        // Insert stuck entry processed by another node
        final BusEvent event2 = new DummyEvent();
        sqlDao.insertEntry(createEntry("thatOtherNode", "thatOtherNode", now, event2, PersistentQueueEntryLifecycleState.IN_PROCESSING), config.getTableName());
        handler.ensureNotSeen(event2);
        handler.assertSeenEvents(1);

        // Post another event
        final BusEvent event3 = new DummyEvent();
        bus.post(event3);
        handler.waitFor(event3);
        handler.ensureNotSeen(event2);
        handler.assertSeenEvents(2);

        // Trigger reaper
        clock.addDeltaFromReality(config.getReapThreshold().getMillis());
        handler.waitFor(event2);
        handler.assertSeenEvents(3);
    }

    @Test(groups = "slow")
    public void testWithStuckEntryNonExistingNode() throws EventBusException, JsonProcessingException, InterruptedException {
        final DateTime now = clock.getUTCNow();

        // Verify bus is working
        final BusEvent event1 = new DummyEvent();
        bus.post(event1);
        handler.waitFor(event1);
        handler.assertSeenEvents(1);

        // Insert entry processed by no-one
        final BusEvent event2 = new DummyEvent();
        sqlDao.insertEntry(createEntry("aws-compute-123456.internal", null, now, event2, PersistentQueueEntryLifecycleState.AVAILABLE), config.getTableName());
        handler.ensureNotSeen(event2);
        handler.assertSeenEvents(1);

        // Post another event
        final BusEvent event3 = new DummyEvent();
        bus.post(event3);
        handler.waitFor(event3);
        handler.ensureNotSeen(event2);
        handler.assertSeenEvents(2);

        // Trigger reaper
        clock.addDeltaFromReality(config.getReapThreshold().getMillis());
        handler.waitFor(event2);
        handler.assertSeenEvents(3);
    }

    @Test(groups = "slow")
    public void testWithStuckEntryProcessedByThisNode() throws EventBusException, JsonProcessingException, InterruptedException {
        final DateTime now = clock.getUTCNow();

        // Verify bus is working
        final BusEvent event1 = new DummyEvent();
        bus.post(event1);
        handler.waitFor(event1);
        handler.assertSeenEvents(1);

        // Insert stuck entry processed by this node
        final BusEvent event2 = new DummyEvent();
        sqlDao.insertEntry(createEntry(CreatorName.get(), CreatorName.get(), now, event2, PersistentQueueEntryLifecycleState.IN_PROCESSING), config.getTableName());
        handler.ensureNotSeen(event2);
        handler.assertSeenEvents(1);

        // Post another event
        final BusEvent event3 = new DummyEvent();
        bus.post(event3);
        handler.waitFor(event3);
        handler.ensureNotSeen(event2);
        handler.assertSeenEvents(2);

        // Trigger reaper
        clock.addDeltaFromReality(config.getReapThreshold().getMillis());
        // It's a no-op though (it won't reap itself)
        handler.ensureNotSeen(event2);
        handler.assertSeenEvents(2);
    }


    @Test(groups = "slow")
    public void testWithLateBusOnThisNode() throws EventBusException, JsonProcessingException, InterruptedException {
        final DateTime now = clock.getUTCNow();

        // Verify bus is working
        final BusEvent event1 = new DummyEvent();
        bus.post(event1);
        handler.waitFor(event1);
        handler.assertSeenEvents(1);

        // Insert old entry not yet processed by this node
        final BusEvent event2 = new DummyEvent();
        // PersistentQueueEntryLifecycleState.AVAILABLE would be more accurate, but this is just to trick the bus not to pick that entry up
        sqlDao.insertEntry(createEntry(CreatorName.get(), null, now.minusHours(2), null, event2, PersistentQueueEntryLifecycleState.IN_PROCESSING), config.getTableName());
        handler.ensureNotSeen(event2);
        handler.assertSeenEvents(1);

        // Post another event
        final BusEvent event3 = new DummyEvent();
        bus.post(event3);
        handler.waitFor(event3);
        handler.ensureNotSeen(event2);
        handler.assertSeenEvents(2);

        // The reaper will never reap it
        clock.addDeltaFromReality(config.getReapThreshold().getMillis());
        handler.ensureNotSeen(event2);
        handler.assertSeenEvents(2);
    }

    @Test(groups = "slow")
    public void testWithLateBusOnAnotherNode() throws EventBusException, JsonProcessingException, InterruptedException {
        final DateTime now = clock.getUTCNow();

        // Verify bus is working
        final BusEvent event1 = new DummyEvent();
        bus.post(event1);
        handler.waitFor(event1);
        handler.assertSeenEvents(1);

        // Insert old entry processed by another node (nextAvailableDate reflects that it just got picked up)
        final BusEvent event2 = new DummyEvent();
        sqlDao.insertEntry(createEntry("thatOtherNode", "thatOtherNode", now.minusHours(2), now.plus(config.getClaimedTime().getMillis()), event2, PersistentQueueEntryLifecycleState.IN_PROCESSING), config.getTableName());
        handler.ensureNotSeen(event2);
        handler.assertSeenEvents(1);

        // Post another event
        final BusEvent event3 = new DummyEvent();
        bus.post(event3);
        handler.waitFor(event3);
        handler.ensureNotSeen(event2);
        handler.assertSeenEvents(2);

        // After a while though, the reaper will be triggered
        clock.addDeltaFromReality(config.getReapThreshold().getMillis());
        handler.waitFor(event2);
        handler.assertSeenEvents(3);
    }

    private BusEventModelDao createEntry(final String creatingOwner,
                                         final String processingOwner,
                                         final DateTime createdDate,
                                         final BusEvent event,
                                         final PersistentQueueEntryLifecycleState state) throws JsonProcessingException {
        final DateTime processingAvailableDate = createdDate.plus(config.getClaimedTime().getMillis());
        return createEntry(creatingOwner, processingOwner, createdDate, processingAvailableDate, event, state);
    }

    private BusEventModelDao createEntry(final String creatingOwner,
                                         final String processingOwner,
                                         final DateTime createdDate,
                                         final DateTime processingAvailableDate,
                                         final BusEvent event,
                                         final PersistentQueueEntryLifecycleState state) throws JsonProcessingException {
        return new BusEventModelDao(null,
                                    creatingOwner,
                                    processingOwner,
                                    createdDate,
                                    processingAvailableDate,
                                    state,
                                    event.getClass().getName(),
                                    QueueObjectMapper.get().writeValueAsString(event),
                                    0L,
                                    event.getUserToken(),
                                    event.getSearchKey1(),
                                    event.getSearchKey2());
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

        public DummyEvent() {
            this(UUID.randomUUID().toString(),
                 System.currentTimeMillis(),
                 System.currentTimeMillis(),
                 UUID.randomUUID());
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

        @Override
        public String toString() {
            final StringBuffer sb = new StringBuffer("DummyEvent{");
            sb.append("name='").append(name).append('\'');
            sb.append(", searchKey1=").append(searchKey1);
            sb.append(", searchKey2=").append(searchKey2);
            sb.append(", userToken=").append(userToken);
            sb.append('}');
            return sb.toString();
        }
    }

    public static class DummyHandler {

        private final Set<UUID> receivedEvents;

        DummyHandler() {
            receivedEvents = new HashSet<>();
        }

        @AllowConcurrentEvents
        @Subscribe
        public void processEvent(final BusEvent event) {
            receivedEvents.add(event.getUserToken());
        }

        void waitFor(final BusEvent event) {
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return receivedEvents.contains(event.getUserToken());
                }
            });
        }

        void ensureNotSeen(final BusEvent event) throws InterruptedException {
            Thread.sleep(1000);
            assertFalse(receivedEvents.contains(event.getUserToken()));
        }

        void assertSeenEvents(final int expected) {
            assertEquals(receivedEvents.size(), expected);
        }
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
                return 0;
            }

            @Override
            public int getMaxInFlightEntries() {
                return 0;
            }

            @Override
            public int getMaxEntriesClaimed() {
                return 10;
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
                return 1;
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
                return 1;
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
                // Aggressive on purpose
                return new TimeSpan(1, TimeUnit.SECONDS);
            }
        };
    }
}
