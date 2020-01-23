/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.notificationq.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.CreatorName;
import org.killbill.TestSetup;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.dao.QueueSqlDao;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.Collections2;
import com.google.common.collect.Iterators;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestNotificationSqlDao extends TestSetup {

    private static final String hostname = "Yop";

    private static final long SEARCH_KEY_2 = 37;

    private NotificationSqlDao dao;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        super.beforeClass();
        dao = getDBI().onDemand(NotificationSqlDao.class);
    }

    @Test(groups = "slow", description = "Verify SQL bound arguments are properly serialized")
    public void testQueryGeneratesNoWarning() throws Exception {
        final Handle handle = getDBI().open();
        try {
            final Date date = new DateTime().toDate();
            handle.inTransaction(new TransactionCallback<Object>() {
                @Override
                public Object inTransaction(final Handle conn, final TransactionStatus status) throws Exception {
                    final NotificationSqlDao notificationSqlDao = conn.attach(NotificationSqlDao.class);
                    final List<NotificationEventModelDao> entries = notificationSqlDao.getReadyEntries(date, 3, hostname, notificationQueueConfig.getTableName());
                    assertNull(conn.getConnection().getWarnings());
                    assertEquals(entries.size(), 0);
                    return null;
                }
            });
        } finally {
            handle.close();
        }
    }

    @Test(groups = "slow")
    public void testBasic() throws InterruptedException {
        final long searchKey1 = 1242L;
        final String ownerId = UUID.randomUUID().toString();

        final String eventJson = UUID.randomUUID().toString();
        final DateTime effDt = new DateTime();

        final NotificationEventModelDao notif = new NotificationEventModelDao(hostname, clock.getUTCNow(), eventJson.getClass().getName(),
                eventJson, UUID.randomUUID(), searchKey1, SEARCH_KEY_2,
                UUID.randomUUID(), effDt, "testBasic");

        dao.insertEntry(notif, notificationQueueConfig.getTableName());

        Thread.sleep(1000);
        final DateTime now = new DateTime();
        final List<NotificationEventModelDao> notifications = dao.getReadyEntries(now.toDate(), 3, hostname, notificationQueueConfig.getTableName());
        assertNotNull(notifications);
        assertEquals(notifications.size(), 1);

        final long nbEntries = dao.getNbReadyEntries(now.toDate(), hostname, notificationQueueConfig.getTableName());
        assertEquals(nbEntries, 1);

        NotificationEventModelDao notification = notifications.get(0);
        assertEquals(notification.getEventJson(), eventJson);
        validateDate(notification.getEffectiveDate(), effDt);
        assertEquals(notification.getProcessingOwner(), null);
        assertEquals(notification.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);
        assertEquals(notification.getNextAvailableDate(), null);


        final DateTime nextAvailable = now.plusMinutes(5);
        final int res = dao.claimEntry(notification.getRecordId(), ownerId, nextAvailable.toDate(), notificationQueueConfig.getTableName());
        assertEquals(res, 1);
        dao.claimEntry(notification.getRecordId(), ownerId, nextAvailable.toDate(), notificationQueueConfig.getTableName());

        notification = dao.getByRecordId(notification.getRecordId(), notificationQueueConfig.getTableName());
        assertEquals(notification.getEventJson(), eventJson);
        validateDate(notification.getEffectiveDate(), effDt);
        assertEquals(notification.getProcessingOwner(), ownerId);
        assertEquals(notification.getProcessingState(), PersistentQueueEntryLifecycleState.IN_PROCESSING);
        validateDate(notification.getNextAvailableDate(), nextAvailable);

        final DateTime processedTime = clock.getUTCNow();
        NotificationEventModelDao notificationHistory = new NotificationEventModelDao(notification, CreatorName.get(), processedTime, PersistentQueueEntryLifecycleState.PROCESSED);
        dao.insertEntry(notificationHistory, notificationQueueConfig.getHistoryTableName());

        notificationHistory = dao.getByRecordId(notification.getRecordId(), notificationQueueConfig.getHistoryTableName());
        assertEquals(notificationHistory.getEventJson(), eventJson);
        validateDate(notificationHistory.getEffectiveDate(), effDt);
        assertEquals(notificationHistory.getProcessingOwner(), CreatorName.get());
        assertEquals(notificationHistory.getProcessingState(), PersistentQueueEntryLifecycleState.PROCESSED);
        validateDate(notificationHistory.getNextAvailableDate(), processedTime);

        dao.removeEntry(notification.getRecordId(), notificationQueueConfig.getTableName());
        notification = dao.getByRecordId(notification.getRecordId(), notificationQueueConfig.getTableName());
        assertNull(notification);
    }

    @Test(groups = "slow")
    public void testBatchOperations() throws InterruptedException {
        final long searchKey1 = 1242L;

        final String eventJson = UUID.randomUUID().toString();
        final DateTime effDt = new DateTime();

        NotificationEventModelDao notif1 = new NotificationEventModelDao(hostname, clock.getUTCNow(), eventJson.getClass().getName(),
                eventJson, UUID.randomUUID(), searchKey1, SEARCH_KEY_2,
                UUID.randomUUID(), effDt, "testBasic1");

        final List<NotificationEventModelDao> entries = new ArrayList<NotificationEventModelDao>();

        notif1 = insertEntry(notif1, notificationQueueConfig.getTableName());
        entries.add(notif1);

        NotificationEventModelDao notif2 = new NotificationEventModelDao(hostname, clock.getUTCNow(), eventJson.getClass().getName(),
                eventJson, UUID.randomUUID(), searchKey1, SEARCH_KEY_2,
                UUID.randomUUID(), effDt, "testBasic2");

        notif2 = insertEntry(notif2, notificationQueueConfig.getTableName());
        entries.add(notif2);

        NotificationEventModelDao notif3 = new NotificationEventModelDao(hostname, clock.getUTCNow(), eventJson.getClass().getName(),
                eventJson, UUID.randomUUID(), searchKey1, SEARCH_KEY_2,
                UUID.randomUUID(), effDt, "testBasic3");

        notif3 = insertEntry(notif3, notificationQueueConfig.getTableName());
        entries.add(notif3);

        final Collection<Long> entryIds = Collections2.transform(entries, new com.google.common.base.Function<NotificationEventModelDao, Long>() {
            @Nullable
            @Override
            public Long apply(@Nullable final NotificationEventModelDao input) {
                return input.getRecordId();
            }
        });

        dao.removeEntries(entryIds, notificationQueueConfig.getTableName());
        for (final Long entry : entryIds) {
            final NotificationEventModelDao result = dao.getByRecordId(entry, notificationQueueConfig.getTableName());
            assertNull(result);
        }

        dao.insertEntries(entries, notificationQueueConfig.getHistoryTableName());
        for (final Long entry : entryIds) {
            final NotificationEventModelDao result = dao.getByRecordId(entry, notificationQueueConfig.getHistoryTableName());
            assertNotNull(result);
        }
    }

    @Test(groups = "slow")
    public void testLargeBatchOperations() {
        final long searchKey1 = 1242L;
        final String ownerId = UUID.randomUUID().toString();


        final List<NotificationEventModelDao> entries = new ArrayList<NotificationEventModelDao>();

        final String eventJson = UUID.randomUUID().toString();
        final DateTime effDt = new DateTime();

        for (int i = 0; i < 2000; i++) {
            NotificationEventModelDao cur = new NotificationEventModelDao(hostname, clock.getUTCNow(), eventJson.getClass().getName(),
                                                                             eventJson, UUID.randomUUID(), searchKey1, SEARCH_KEY_2,
                                                                             UUID.randomUUID(), effDt, "testBasic1");
            entries.add(cur);
        }

        dao.insertEntries(entries, notificationQueueConfig.getTableName());

        final Iterator<NotificationEventModelDao> result = dao.getReadyQueueEntriesForSearchKeys("testBasic1", searchKey1, SEARCH_KEY_2, notificationQueueConfig.getTableName());
        assertEquals(Iterators.size(result), 2000);
    }


        @Test(groups = "slow")
    public void testUpdateEvent() throws InterruptedException {
        final long searchKey1 = 14542L;

        final String eventJsonInitial = "Initial value";
        final DateTime effDt = new DateTime();

        final NotificationEventModelDao notif = new NotificationEventModelDao(hostname, clock.getUTCNow(), eventJsonInitial.getClass().getName(),
                                                                              eventJsonInitial, UUID.randomUUID(), searchKey1, SEARCH_KEY_2,
                                                                              UUID.randomUUID(), effDt, "testUpdateEvent");

        dao.insertEntry(notif, notificationQueueConfig.getTableName());

        Iterator<NotificationEventModelDao> notifications = dao.getReadyOrInProcessingQueueEntriesForSearchKeys(notif.getQueueName(), searchKey1, SEARCH_KEY_2, notificationQueueConfig.getTableName());
        assertTrue(notifications.hasNext());

        final NotificationEventModelDao notification = notifications.next();
        assertEquals(notification.getEventJson(), eventJsonInitial);
        assertFalse(notifications.hasNext());

        final String eventJsonUpdated = "Updated value";
        dao.updateEntry(notification.getRecordId(), eventJsonUpdated, searchKey1, SEARCH_KEY_2, notificationQueueConfig.getTableName());

        notifications = dao.getReadyOrInProcessingQueueEntriesForSearchKeys(notif.getQueueName(), searchKey1, SEARCH_KEY_2, notificationQueueConfig.getTableName());
        assertTrue(notifications.hasNext());

        final NotificationEventModelDao updatedNotification = notifications.next();
        assertEquals(updatedNotification.getEventJson(), eventJsonUpdated);
        assertFalse(notifications.hasNext());

    }

    private NotificationEventModelDao insertEntry(final NotificationEventModelDao input, final String tableName) {
        return dao.inTransaction(new Transaction<NotificationEventModelDao, QueueSqlDao<NotificationEventModelDao>>() {
            @Override
            public NotificationEventModelDao inTransaction(final QueueSqlDao<NotificationEventModelDao> transactional, final TransactionStatus status) throws Exception {
                final Long recordId = transactional.insertEntry(input, tableName);
                return transactional.getByRecordId(recordId, notificationQueueConfig.getTableName());
            }
        });

    }

    private void validateDate(DateTime input, DateTime expected) {
        if (input == null && expected != null) {
            Assert.fail("Got input date null");
        }
        if (input != null && expected == null) {
            Assert.fail("Was expecting null date");
        }
        expected = truncateAndUTC(expected);
        input = truncateAndUTC(input);
        Assert.assertEquals(input, expected);
    }

    private DateTime truncateAndUTC(final DateTime input) {
        if (input == null) {
            return null;
        }
        final DateTime result = input.minus(input.getMillisOfSecond());
        return result.toDateTime(DateTimeZone.UTC);
    }
}
