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

package org.killbill.queue.dispatching;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.awaitility.Awaitility;
import org.joda.time.DateTime;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.api.QueueEvent;
import org.skife.config.TimeSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TestDispatcher {

    private final int QUEUE_SIZE = 5;

    private Dispatcher<BusEvent, BusEventModelDao> dispatcher;
    private TestCallableCallback callback;

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        final ThreadFactory testThreadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(new ThreadGroup("TestGrp"),
                                  r,
                                  "test-grp--th");
            }
        };

        this.callback = new TestCallableCallback();
        this.dispatcher = new Dispatcher<>(1, createConfig(), 5, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>(QUEUE_SIZE), testThreadFactory, new TestBlockingRejectionExecutionHandler(callback),
                                           null, callback, null);
        this.dispatcher.start();
    }

    @Test(groups = "fast")
    public void testBlockingRejectionHandler() {
        callback.block();

        for (int i = 0; i < QUEUE_SIZE + 2; i++) {
            dispatch(i);
        }

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final int size = callback.getProcessed().size();
                return size == QUEUE_SIZE + 2;
            }
        });
    }

    private void dispatch(final int i) {
        final BusEventModelDao e1 = new BusEventModelDao("owner", new DateTime(), String.class.getName(), "e-" + i, UUID.randomUUID(), 1L, 1L);
        dispatcher.dispatch(e1);
    }

    private class TestBlockingRejectionExecutionHandler extends BlockingRejectionExecutionHandler {

        private final TestCallableCallback callback;

        public TestBlockingRejectionExecutionHandler(final TestCallableCallback callback) {
            this.callback = callback;
        }

        @Override
        public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
            callback.unblock();
            super.rejectedExecution(r, executor);
        }

    }

    private class TestCallableCallback implements CallableCallback<BusEvent, BusEventModelDao> {

        private final Logger logger = LoggerFactory.getLogger(TestCallableCallback.class);

        private volatile boolean isBlocked;

        private final List<QueueEvent> processed;

        public void block() {
            synchronized (this) {
                this.isBlocked = true;
            }
        }

        public void unblock() {
            synchronized (this) {
                this.isBlocked = false;
                this.notifyAll();
            }
        }

        public List<QueueEvent> getProcessed() {
            return processed;
        }

        public TestCallableCallback() {
            this.isBlocked = false;
            this.processed = new ArrayList<QueueEvent>();
        }

        @Override
        public BusEvent deserialize(final BusEventModelDao modelDao) {
            return new TestEvent(modelDao.getEventJson(), modelDao.getSearchKey1(), modelDao.getSearchKey2(), modelDao.getUserToken());
        }

        @Override
        public void dispatch(final BusEvent event, final BusEventModelDao modelDao) throws Exception {
            synchronized (this) {
                while (isBlocked) {
                    logger.info("Thread " + Thread.currentThread().getId() + " blocking...");
                    this.wait();
                    logger.info("Thread " + Thread.currentThread().getId() + " unblocking...");
                }
            }
            logger.info("Got entry " + modelDao.getEventJson());
            processed.add(event);
        }

        @Override
        public BusEventModelDao buildEntry(final BusEventModelDao modelDao, final DateTime now, final PersistentQueueEntryLifecycleState newState, final long newErrorCount) {
            return null;
        }

        @Override
        public void moveCompletedOrFailedEvents(final Iterable<BusEventModelDao> entries) {

        }

        @Override
        public void updateRetriedEvents(final BusEventModelDao updatedEntry) {

        }

    }

    public static class TestEvent implements BusEvent {

        private final String json;
        private final Long searchKey1;
        private final Long searchKey2;
        private final UUID userToken;

        @JsonCreator
        public TestEvent(@JsonProperty("json") final String json,
                         @JsonProperty("searchKey1") final Long searchKey1,
                         @JsonProperty("searchKey2") final Long searchKey2,
                         @JsonProperty("userToken") final UUID userToken) {
            this.json = json;
            this.searchKey2 = searchKey2;
            this.searchKey1 = searchKey1;
            this.userToken = userToken;
        }

        public String getJson() {
            return json;
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
                return 1;
            }

            @Override
            public int getMaxEntriesClaimed() {
                return 1;
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
                return 100;
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
                return QUEUE_SIZE;
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
        };
    }
}
