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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.joda.time.Period;
import org.killbill.TestSetup;
import org.killbill.billing.util.queue.QueueRetryException;
import org.killbill.bus.TestEventBusBase.MyEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.bus.dao.PersistentBusSqlDao;
import org.killbill.commons.eventbus.AllowConcurrentEvents;
import org.killbill.commons.eventbus.Subscribe;
import org.killbill.commons.utils.collect.Iterators;
import org.killbill.notificationq.DefaultNotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.dao.NotificationEventModelDao;
import org.killbill.notificationq.dao.NotificationSqlDao;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.retry.RetryableService;
import org.killbill.queue.retry.RetryableSubscriber;
import org.killbill.queue.retry.RetryableSubscriber.SubscriberAction;
import org.killbill.queue.retry.RetryableSubscriber.SubscriberQueueHandler;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.awaitility.Awaitility.await;

public class TestRetries extends TestSetup {

    private static final UUID TOKEN_ID = UUID.randomUUID();
    private static final long SEARCH_KEY_1 = 65;
    private static final long SEARCH_KEY_2 = 34;

    private NotificationQueueService queueService;
    private PersistentBus busService;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        queueService = new DefaultNotificationQueueService(getDBI(), clock, getNotificationQueueConfig(), metricRegistry);
        busService = new DefaultPersistentBus(getDBI(), clock, getPersistentBusConfig(), metricRegistry, databaseTransactionNotificationApi);
        busService.startQueue();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        busService.stopQueue();
    }

    @Test(groups = "slow")
    public void testRetryStateForBus() throws Exception {
        final RetryableBusService retryableBusService = new RetryableBusService(queueService);
        retryableBusService.initialize();
        busService.register(retryableBusService);

        try {
            retryableBusService.start();

            final MyEvent myEvent = new MyEvent("Foo", 1L, "Baz", SEARCH_KEY_1, SEARCH_KEY_2, TOKEN_ID);
            busService.post(myEvent);

            final PersistentBusSqlDao busSqlDao = dbi.onDemand(PersistentBusSqlDao.class);
            final NotificationSqlDao notificationSqlDao = dbi.onDemand(NotificationSqlDao.class);

            // Make sure all retries are processed
            await().atMost(10, TimeUnit.SECONDS)
                   .until(() -> Iterators.size(busSqlDao.getHistoricalQueueEntriesForSearchKeys(SEARCH_KEY_1, SEARCH_KEY_2, persistentBusConfig.getHistoryTableName())) == 1 &&
                                Iterators.size(notificationSqlDao.getHistoricalQueueEntriesForSearchKeys("notifications-retries:myEvent-listener", SEARCH_KEY_1, SEARCH_KEY_2, notificationQueueConfig.getHistoryTableName())) == 3);

            // Initial event was processed once
            List<BusEventModelDao> historicalEntriesForOriginalEvent = Iterators.toUnmodifiableList(busSqlDao.getHistoricalQueueEntriesForSearchKeys(SEARCH_KEY_1, SEARCH_KEY_2, persistentBusConfig.getHistoryTableName()));
            Assert.assertEquals(historicalEntriesForOriginalEvent.size(), 1);
            Assert.assertEquals((long) historicalEntriesForOriginalEvent.get(0).getErrorCount(), (long) 0);
            // State is initially FAILED
            Assert.assertEquals(historicalEntriesForOriginalEvent.get(0).getProcessingState(), PersistentQueueEntryLifecycleState.FAILED);

            // Retry events
            List<NotificationEventModelDao> historicalEntriesForRetries = Iterators.toUnmodifiableList(notificationSqlDao.getHistoricalQueueEntriesForSearchKeys("notifications-retries:myEvent-listener", SEARCH_KEY_1, SEARCH_KEY_2, notificationQueueConfig.getHistoryTableName()));
            Assert.assertEquals(historicalEntriesForRetries.size(), 3);
            for (final NotificationEventModelDao historicalEntriesForRetry : historicalEntriesForRetries) {
                Assert.assertEquals((long) historicalEntriesForRetry.getErrorCount(), (long) 0);
                Assert.assertEquals(historicalEntriesForRetry.getProcessingState(), PersistentQueueEntryLifecycleState.FAILED);
            }

            // Make the next retry work
            retryableBusService.shouldFail(false);

            clock.addDays(1);

            // Make sure all notifications are processed
            await().atMost(10, TimeUnit.SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return Iterators.size(busSqlDao.getHistoricalQueueEntriesForSearchKeys(SEARCH_KEY_1, SEARCH_KEY_2, persistentBusConfig.getHistoryTableName())) == 1 &&
                           Iterators.size(notificationSqlDao.getHistoricalQueueEntriesForSearchKeys("notifications-retries:myEvent-listener", SEARCH_KEY_1, SEARCH_KEY_2, notificationQueueConfig.getHistoryTableName())) == 4;
                }
            });

            // Initial event was processed once
            historicalEntriesForOriginalEvent = Iterators.toUnmodifiableList(busSqlDao.getHistoricalQueueEntriesForSearchKeys(SEARCH_KEY_1, SEARCH_KEY_2, persistentBusConfig.getHistoryTableName()));
            Assert.assertEquals(historicalEntriesForOriginalEvent.size(), 1);
            Assert.assertEquals((long) historicalEntriesForOriginalEvent.get(0).getErrorCount(), (long) 0);
            // State is still FAILED
            Assert.assertEquals(historicalEntriesForOriginalEvent.get(0).getProcessingState(), PersistentQueueEntryLifecycleState.FAILED);

            // Retry events
            historicalEntriesForRetries = Iterators.toUnmodifiableList(notificationSqlDao.getHistoricalQueueEntriesForSearchKeys("notifications-retries:myEvent-listener", SEARCH_KEY_1, SEARCH_KEY_2, notificationQueueConfig.getHistoryTableName()));
            Assert.assertEquals(historicalEntriesForRetries.size(), 4);
            for (int i = 0; i < historicalEntriesForRetries.size(); i++) {
                final NotificationEventModelDao historicalEntriesForRetry = historicalEntriesForRetries.get(i);
                Assert.assertEquals((long) historicalEntriesForRetry.getErrorCount(), (long) 0);
                Assert.assertEquals(historicalEntriesForRetry.getProcessingState(), i == historicalEntriesForRetries.size() - 1 ? PersistentQueueEntryLifecycleState.PROCESSED : PersistentQueueEntryLifecycleState.FAILED);
            }
        } finally {
            retryableBusService.stop();
        }
    }

    private final class RetryableBusService extends RetryableService {

        private final SubscriberQueueHandler subscriberQueueHandler = new SubscriberQueueHandler();
        private final RetryableSubscriber retryableSubscriber;

        private boolean shouldFail = true;

        public RetryableBusService(final NotificationQueueService notificationQueueService) {
            super(notificationQueueService);

            subscriberQueueHandler.subscribe(MyEvent.class,
                                             new SubscriberAction<MyEvent>() {
                                                 @Override
                                                 public void run(final MyEvent event) {
                                                     if (!shouldFail) {
                                                         return;
                                                     }

                                                     final NullPointerException exceptionForTests = new NullPointerException("Expected exception for tests");

                                                     // 4 retries
                                                     throw new QueueRetryException(exceptionForTests, List.of(Period.millis(1),
                                                                                                              Period.millis(1),
                                                                                                              Period.millis(1),
                                                                                                              Period.days(1)));
                                                 }
                                             });
            retryableSubscriber = new RetryableSubscriber(clock, this, subscriberQueueHandler);
        }

        public void initialize() {
            super.initialize("myEvent-listener", subscriberQueueHandler);
        }

        @AllowConcurrentEvents
        @Subscribe
        public void handleMyEvent(final MyEvent event) {
            retryableSubscriber.handleEvent(event);
        }

        public void shouldFail(final boolean shouldFail) {
            this.shouldFail = shouldFail;
        }
    }
}
