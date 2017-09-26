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

package org.killbill.notificationq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.joda.time.DateTime;
import org.killbill.TestSetup;
import org.killbill.clock.ClockMock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.util.IntegerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.testng.Assert.assertEquals;

public class TestNotificationQueue extends TestSetup {

    private final Logger log = LoggerFactory.getLogger(TestNotificationQueue.class);

    private static final UUID TOKEN_ID = UUID.randomUUID();
    private static final long SEARCH_KEY_1 = 65;
    private static final long SEARCH_KEY_2 = 34;

    private NotificationQueueService queueService;

    private volatile int eventsReceived;

    private static final class TestNotificationKey implements NotificationEvent, Comparable<TestNotificationKey> {

        private final String value;

        @JsonCreator
        public TestNotificationKey(@JsonProperty("value") final String value) {
            super();
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public int compareTo(final TestNotificationKey arg0) {
            return value.compareTo(arg0.value);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append(value);
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TestNotificationKey)) {
                return false;
            }

            final TestNotificationKey that = (TestNotificationKey) o;

            if (value != null ? !value.equals(that.value) : that.value != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return value != null ? value.hashCode() : 0;
        }
    }

    @Override
    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        super.beforeClass();
    }

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        queueService = new DefaultNotificationQueueService(getDBI(), clock, getNotificationQueueConfig(), metricRegistry);
        eventsReceived = 0;
    }

    /**
     * Test that we can post a notification in the future from a transaction and get the notification
     * callback with the correct key when the time is ready
     *
     * @throws Exception
     */
    @Test(groups = "slow")
    public void testSimpleNotification() throws Exception {

        final Map<NotificationEvent, Boolean> expectedNotifications = new TreeMap<NotificationEvent, Boolean>();

        final NotificationQueue queue = queueService.createNotificationQueue("test-svc",
                "foo",
                new NotificationQueueHandler() {
                    @Override
                    public void handleReadyNotification(final NotificationEvent eventJson, final DateTime eventDateTime, final UUID userToken, final Long searchKey1, final Long searchKey2) {
                        synchronized (expectedNotifications) {
                            log.info("Handler received key: " + eventJson);

                            expectedNotifications.put(eventJson, Boolean.TRUE);
                            expectedNotifications.notify();
                        }
                    }
                });

        queue.startQueue();

        final DateTime now = new DateTime();
        final DateTime readyTime = now.plusMillis(2000);


        final UUID key1 = UUID.randomUUID();
        final NotificationEvent eventJson1 = new TestNotificationKey(key1.toString());
        expectedNotifications.put(eventJson1, Boolean.FALSE);

        final DBI dbi = getDBI();
        dbi.inTransaction(new TransactionCallback<Object>() {
            @Override
            public Object inTransaction(final Handle conn, final TransactionStatus status) throws Exception {
                queue.recordFutureNotificationFromTransaction(conn.getConnection(), readyTime, eventJson1, TOKEN_ID, 1L, SEARCH_KEY_2);
                log.info("Posted key: " + eventJson1);
                return null;
            }
        });

        final UUID key2 = UUID.randomUUID();
        final NotificationEvent eventJson2 = new TestNotificationKey(key2.toString());
        expectedNotifications.put(eventJson2, Boolean.FALSE);

        dbi.inTransaction(new TransactionCallback<Object>() {
            @Override
            public Object inTransaction(final Handle conn, final TransactionStatus status) throws Exception {
                queue.recordFutureNotificationFromTransaction(conn.getConnection(), readyTime, eventJson2, TOKEN_ID, SEARCH_KEY_1, 1L);
                log.info("Posted key: " + eventJson2);
                return null;
            }
        });

        final UUID key3 = UUID.randomUUID();
        final NotificationEvent eventJson3 = new TestNotificationKey(key3.toString());
        expectedNotifications.put(eventJson3, Boolean.FALSE);

        dbi.inTransaction(new TransactionCallback<Object>() {
            @Override
            public Object inTransaction(final Handle conn, final TransactionStatus status) throws Exception {
                queue.recordFutureNotificationFromTransaction(conn.getConnection(), readyTime, eventJson3, TOKEN_ID, SEARCH_KEY_1, SEARCH_KEY_2);
                log.info("Posted key: " + eventJson3);
                return null;
            }
        });

        final UUID key4 = UUID.randomUUID();
        final NotificationEvent eventJson4 = new TestNotificationKey(key4.toString());
        expectedNotifications.put(eventJson4, Boolean.FALSE);
        dbi.inTransaction(new TransactionCallback<Object>() {
            @Override
            public Object inTransaction(final Handle conn, final TransactionStatus status) throws Exception {
                queue.recordFutureNotificationFromTransaction(conn.getConnection(), readyTime, eventJson4, TOKEN_ID, SEARCH_KEY_1, SEARCH_KEY_2);
                log.info("Posted key: " + eventJson4);
                return null;
            }
        });

        Assert.assertEquals(Iterables.<NotificationEventWithMetadata<TestNotificationKey>>size(queue.getInProcessingNotifications()), 0);

        final List<NotificationEventWithMetadata> futuresAll = ImmutableList.<NotificationEventWithMetadata>copyOf(queue.getFutureNotificationForSearchKeys(SEARCH_KEY_1, SEARCH_KEY_2));
        Assert.assertEquals(futuresAll.size(), 2);
        int found = 0;
        for (int i = 0; i < 2; i++) {
            final TestNotificationKey testNotificationKey = (TestNotificationKey) futuresAll.get(i).getEvent();
            if (testNotificationKey.getValue().equals(key3.toString()) ||
                testNotificationKey.getValue().equals(key4.toString())) {
                found++;
            }
        }
        Assert.assertEquals(found, 2);


        final List<NotificationEventWithMetadata> futures2 = ImmutableList.<NotificationEventWithMetadata>copyOf(queue.getFutureNotificationForSearchKey2(null, SEARCH_KEY_2));
        Assert.assertEquals(futures2.size(), 3);
        found = 0;
        for (int i = 0; i < 3; i++) {
            final TestNotificationKey testNotificationKey = (TestNotificationKey) futures2.get(i).getEvent();
            if (testNotificationKey.getValue().equals(key3.toString()) ||
                testNotificationKey.getValue().equals(key4.toString()) ||
                testNotificationKey.getValue().equals(key1.toString())) {
                found++;
            }
        }
        Assert.assertEquals(found, 3);

        // Move time in the future after the notification effectiveDate
        clock.setDeltaFromReality(3000);


        // Notification should have kicked but give it at least a sec' for thread scheduling
        await().atMost(1, MINUTES)
                .until(new Callable<Boolean>() {
                           @Override
                           public Boolean call() throws
                                   Exception {
                               return expectedNotifications.get(eventJson1) &&
                                       expectedNotifications.get(eventJson2) &&
                                       expectedNotifications.get(eventJson3) &&
                                       expectedNotifications.get(eventJson4);
                           }
                       }
                );
        queue.stopQueue();
    }

    @Test(groups = "slow")
    public void testManyNotifications() throws Exception {
        final Map<NotificationEvent, Boolean> expectedNotifications = new TreeMap<NotificationEvent, Boolean>();

        final NotificationQueue queue = queueService.createNotificationQueue("test-svc",
                "many",
                new NotificationQueueHandler() {
                    @Override
                    public void handleReadyNotification(final NotificationEvent eventJson, final DateTime eventDateTime, final UUID userToken, final Long searchKey1, final Long searchKey2) {
                        synchronized (expectedNotifications) {
                            log.info("Handler received key: " + eventJson.toString());

                            expectedNotifications.put(eventJson, Boolean.TRUE);
                            expectedNotifications.notify();
                        }
                    }
                });
        queue.startQueue();

        final DateTime now = clock.getUTCNow();
        final int MAX_NOTIFICATIONS = 100;
        for (int i = 0; i < MAX_NOTIFICATIONS; i++) {

            final int nextReadyTimeIncrementMs = 1000;

            final UUID key = UUID.randomUUID();
            final int currentIteration = i;

            final NotificationEvent eventJson = new TestNotificationKey(new Integer(i).toString());
            expectedNotifications.put(eventJson, Boolean.FALSE);

            final DBI dbi = getDBI();
            dbi.inTransaction(new TransactionCallback<Object>() {
                @Override
                public Object inTransaction(final Handle conn, final TransactionStatus status) throws Exception {
                    queue.recordFutureNotificationFromTransaction(conn.getConnection(), now.plus((currentIteration + 1) * nextReadyTimeIncrementMs),
                            eventJson, TOKEN_ID, SEARCH_KEY_1, SEARCH_KEY_2);
                    return null;
                }
            });

            // Move time in the future after the notification effectiveDate
            if (i == 0) {
                clock.setDeltaFromReality(nextReadyTimeIncrementMs);
            } else {
                clock.addDeltaFromReality(nextReadyTimeIncrementMs);
            }
        }

        // Wait a little longer since there are a lot of callback that need to happen
        int nbTry = MAX_NOTIFICATIONS + 1;
        boolean success = false;
        do {
            synchronized (expectedNotifications) {
                final Collection<Boolean> completed = Collections2.filter(expectedNotifications.values(), new Predicate<Boolean>() {
                    @Override
                    public boolean apply(final Boolean input) {
                        return input;
                    }
                });

                if (completed.size() == MAX_NOTIFICATIONS) {
                    success = true;
                    break;
                }
                log.info(String.format("BEFORE WAIT : Got %d notifications at time %s (real time %s)", completed.size(), clock.getUTCNow(), new DateTime()));
                expectedNotifications.wait(1000);
            }
        } while (nbTry-- > 0);

        queue.stopQueue();
        log.info("GOT SIZE " + Collections2.filter(expectedNotifications.values(), new Predicate<Boolean>() {
            @Override
            public boolean apply(final Boolean input) {
                return input;
            }
        }).size());
        assertEquals(success, true);
    }

    /**
     * Test that we can post a notification in the future from a transaction and get the notification
     * callback with the correct key when the time is ready
     *
     * @throws Exception
     */
    @Test(groups = "slow")
    public void testMultipleHandlerNotification() throws Exception {
        final Map<NotificationEvent, Boolean> expectedNotificationsFred = new TreeMap<NotificationEvent, Boolean>();
        final Map<NotificationEvent, Boolean> expectedNotificationsBarney = new TreeMap<NotificationEvent, Boolean>();

        final NotificationQueue queueFred = queueService.createNotificationQueue("UtilTest", "Fred", new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(final NotificationEvent eventJson, final DateTime eventDateTime, final UUID userToken, final Long searchKey1, final Long searchKey2) {
                log.info("Fred received key: " + eventJson);
                expectedNotificationsFred.put(eventJson, Boolean.TRUE);
                eventsReceived++;
            }
        });

        final NotificationQueue queueBarney = queueService.createNotificationQueue("UtilTest", "Barney", new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(final NotificationEvent eventJson, final DateTime eventDateTime, final UUID userToken, final Long searchKey1, final Long searchKey2) {
                log.info("Barney received key: " + eventJson);
                expectedNotificationsBarney.put(eventJson, Boolean.TRUE);
                eventsReceived++;
            }
        });
        queueFred.startQueue();
        //		We don't start Barney so it can never pick up notifications

        final UUID key = UUID.randomUUID();
        final DateTime now = new DateTime();
        final DateTime readyTime = now.plusMillis(2000);
        final NotificationEvent eventJsonFred = new TestNotificationKey("Fred");

        final NotificationEvent eventJsonBarney = new TestNotificationKey("Barney");

        expectedNotificationsFred.put(eventJsonFred, Boolean.FALSE);
        expectedNotificationsFred.put(eventJsonBarney, Boolean.FALSE);

        final DBI dbi = getDBI();
        dbi.inTransaction(new TransactionCallback<Object>() {
            @Override
            public Object inTransaction(final Handle conn, final TransactionStatus status) throws Exception {
                queueFred.recordFutureNotificationFromTransaction(conn.getConnection(), readyTime, eventJsonFred, TOKEN_ID, SEARCH_KEY_1, SEARCH_KEY_2);
                log.info("posted key: " + eventJsonFred.toString());
                queueBarney.recordFutureNotificationFromTransaction(conn.getConnection(), readyTime, eventJsonBarney, TOKEN_ID, SEARCH_KEY_1, SEARCH_KEY_2);
                log.info("posted key: " + eventJsonBarney.toString());
                return null;
            }
        });

        // Move time in the future after the notification effectiveDate
        clock.setDeltaFromReality(3000);

        // Note the timeout is short on this test, but expected behaviour is that it times out.
        // We are checking that the Fred queue does not pick up the Barney event
        try {
            await().atMost(5, TimeUnit.SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return eventsReceived >= 2;
                }
            });
            Assert.fail("There should only have been one event for the queue to pick up - it got more than that");
        } catch (final Exception e) {
            // expected behavior
        }

        queueFred.stopQueue();
        Assert.assertTrue(expectedNotificationsFred.get(eventJsonFred));
        Assert.assertFalse(expectedNotificationsFred.get(eventJsonBarney));
    }

    private class NotificationQueueHandlerWithExceptions implements NotificationQueueHandler {

        private final int nbTotalExceptionsToThrow;
        private int nbExceptionsThrown;

        public NotificationQueueHandlerWithExceptions(final int nbTotalExceptionsToThrow) {
            this.nbTotalExceptionsToThrow = nbTotalExceptionsToThrow;
            this.nbExceptionsThrown = 0;
        }


        @Override
        public void handleReadyNotification(final NotificationEvent eventJson, final DateTime eventDateTime, final UUID userToken, final Long searchKey1, final Long searchKey2) {

            //Assert.assertEquals(((DefaultNotificationQueueService) queueService).getDao().getSqlDao().getInProcessingEntries(notificationQueueConfig.getTableName()).size(), 1);

            if (nbExceptionsThrown < nbTotalExceptionsToThrow) {
                nbExceptionsThrown++;
                throw new NullPointerException();
            }
            eventsReceived++;
        }
    }

    @Test(groups = "slow")
    public void testWithExceptionAndRetrySuccess() throws Exception {

        final NotificationQueue queueWithExceptionAndRetrySuccess = queueService.createNotificationQueue("ExceptionAndRetrySuccess", "svc", new NotificationQueueHandlerWithExceptions(1));
        try {
            queueWithExceptionAndRetrySuccess.startQueue();

            final DateTime now = new DateTime();
            final DateTime readyTime = now.plusMillis(2000);
            final NotificationEvent eventJson = new TestNotificationKey("Foo");

            queueWithExceptionAndRetrySuccess.recordFutureNotification(readyTime, eventJson, TOKEN_ID, SEARCH_KEY_1, SEARCH_KEY_2);

            // Move time in the future after the notification effectiveDate
            clock.setDeltaFromReality(3000);

            await().atMost(5, TimeUnit.SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {

                    final Integer retryCount = dbi.withHandle(new HandleCallback<Integer>() {
                        @Override
                        public Integer withHandle(final Handle handle) throws Exception {
                            return handle.createQuery(String.format("select error_count from %s", notificationQueueConfig.getHistoryTableName())).map(IntegerMapper.FIRST).first();
                        }
                    });
                    return retryCount != null && retryCount == 1 && eventsReceived == 1;
                }
            });
        } finally {
            queueWithExceptionAndRetrySuccess.stopQueue();
        }
    }



    @Test(groups = "slow")
    public void testWithExceptionAndFailed() throws Exception {

        final NotificationQueue queueWithExceptionAndFailed = queueService.createNotificationQueue("ExceptionAndRetrySuccess", "svc", new NotificationQueueHandlerWithExceptions(3));
        try {
            queueWithExceptionAndFailed.startQueue();

            final DateTime now = new DateTime();
            final DateTime readyTime = now.plusMillis(2000);
            final NotificationEvent eventJson = new TestNotificationKey("Foo");

            queueWithExceptionAndFailed.recordFutureNotification(readyTime, eventJson, TOKEN_ID, SEARCH_KEY_1, SEARCH_KEY_2);

            // Move time in the future after the notification effectiveDate
            clock.setDeltaFromReality(3000);

            await().atMost(5, TimeUnit.SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {

                    final Integer retryCount = dbi.withHandle(new HandleCallback<Integer>() {
                        @Override
                        public Integer withHandle(final Handle handle) throws Exception {
                            return handle.createQuery(String.format("select error_count from %s", notificationQueueConfig.getHistoryTableName())).map(IntegerMapper.FIRST).first();
                        }
                    });
                    return retryCount != null && retryCount == 3;
                }
            });
        } finally {
            queueWithExceptionAndFailed.stopQueue();
        }
   }
}
