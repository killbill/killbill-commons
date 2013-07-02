/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.notificationq.dao;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.Hostname;
import com.ning.billing.TestSetup;
import com.ning.billing.notificationq.DefaultNotificationQueue;
import com.ning.billing.queue.api.EventEntry.PersistentQueueEntryLifecycleState;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestNotificationSqlDao extends TestSetup {

    private static final String hostname = "Yop";

    private static final long SEARCH_KEY_2 = 37;

    private NotificationSqlDao dao;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        super.beforeClass();
        dao = getDBI().onDemand(NotificationSqlDao.class);
    }

    @Test(groups = "slow")
    public void testBasic() throws InterruptedException {
        final long searchKey1 = 1242L;
        final String ownerId = UUID.randomUUID().toString();

        final String eventJson = UUID.randomUUID().toString();
        final DateTime effDt = new DateTime();

        final NotificationEventEntry notif = new NotificationEventEntry(Hostname.get(), clock.getUTCNow(), eventJson.getClass().getName(),
                                                                        eventJson, UUID.randomUUID(), searchKey1, SEARCH_KEY_2,
                                                                        UUID.randomUUID(), effDt, "testBasic");

        dao.insertEntry(notif, DefaultNotificationQueue.NOTIFICATION_QUEUE_TABLE_NAME);

        Thread.sleep(1000);
        final DateTime now = new DateTime();
        final List<NotificationEventEntry> notifications = dao.getReadyEntries(now.toDate(), hostname, 3, DefaultNotificationQueue.NOTIFICATION_QUEUE_TABLE_NAME);
        assertNotNull(notifications);
        assertEquals(notifications.size(), 1);

        NotificationEventEntry notification = notifications.get(0);
        assertEquals(notification.getEventJson(), eventJson);
        validateDate(notification.getEffectiveDate(), effDt);
        assertEquals(notification.getProcessingOwner(), null);
        assertEquals(notification.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);
        assertEquals(notification.getNextAvailableDate(), null);


        final DateTime nextAvailable = now.plusMinutes(5);
        final int res = dao.claimEntry(notification.getRecordId(), now.toDate(), ownerId, nextAvailable.toDate(),  DefaultNotificationQueue.NOTIFICATION_QUEUE_TABLE_NAME);
        assertEquals(res, 1);
        dao.claimEntry(notification.getRecordId(), now.toDate(),  ownerId, nextAvailable.toDate(), DefaultNotificationQueue.NOTIFICATION_QUEUE_TABLE_NAME);

        notification = dao.getByRecordId(notification.getRecordId(), DefaultNotificationQueue.NOTIFICATION_QUEUE_TABLE_NAME);
        assertEquals(notification.getEventJson(), eventJson);
        validateDate(notification.getEffectiveDate(), effDt);
        assertEquals(notification.getProcessingOwner(), ownerId);
        assertEquals(notification.getProcessingState(), PersistentQueueEntryLifecycleState.IN_PROCESSING);
        validateDate(notification.getNextAvailableDate(), nextAvailable);

        final DateTime processedTime = clock.getUTCNow();
        NotificationEventEntry notificationHistory = new NotificationEventEntry(notification, Hostname.get(), processedTime, PersistentQueueEntryLifecycleState.PROCESSED);
        dao.insertEntry(notificationHistory, DefaultNotificationQueue.NOTIFICATION_QUEUE_HISTORY_TABLE_NAME);

        notificationHistory = dao.getByRecordId(notification.getRecordId(), DefaultNotificationQueue.NOTIFICATION_QUEUE_HISTORY_TABLE_NAME);
        assertEquals(notificationHistory.getEventJson(), eventJson);
        validateDate(notificationHistory.getEffectiveDate(), effDt);
        assertEquals(notificationHistory.getProcessingOwner(), Hostname.get());
        assertEquals(notificationHistory.getProcessingState(), PersistentQueueEntryLifecycleState.PROCESSED);
        validateDate(notificationHistory.getNextAvailableDate(), processedTime);

        dao.removeEntry(notification.getRecordId(), DefaultNotificationQueue.NOTIFICATION_QUEUE_TABLE_NAME);
        notification = dao.getByRecordId(notification.getRecordId(), DefaultNotificationQueue.NOTIFICATION_QUEUE_TABLE_NAME);
        assertNull(notification);
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
