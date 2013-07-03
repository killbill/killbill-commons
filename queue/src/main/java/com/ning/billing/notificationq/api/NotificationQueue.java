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

package com.ning.billing.notificationq.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.notificationq.dao.NotificationEventEntry;
import com.ning.billing.notificationq.dao.NotificationSqlDao;
import com.ning.billing.queue.api.QueueLifecycle;


public interface NotificationQueue extends QueueLifecycle {

    /**
     * Record the need to be called back when the notification is ready
     *
     * @param futureNotificationTime the time at which the notification is ready
     * @param eventJson        the key for that notification
     */
    public void recordFutureNotification(final DateTime futureNotificationTime,
                                         final NotificationEventBase eventJson,
                                         final UUID userToken,
                                         final Long searchKey1,
                                         final Long searchKey2)
            throws IOException;

    /**
     * Record from within a transaction the need to be called back when the notification is ready
     *
     * @param futureNotificationTime the time at which the notification is ready
     * @param eventJson        the key for that notification
     */
    public void recordFutureNotificationFromTransaction(final Transmogrifier transmogrifier,
                                                        final DateTime futureNotificationTime,
                                                        final NotificationEventBase eventJson,
                                                        final UUID userToken,
                                                        final Long searchKey1,
                                                        final Long searchKey2)
            throws IOException;


    /**
     * Retrieve all future pending notifications for a given account (taken from the context) in a transaction.
     * Results are ordered by effective date asc.
     *
     * @return future notifications matching that key
     */
    public  <T extends NotificationEventBase> List<NotificationEvent<T>> getFutureNotificationForSearchKey1(final Class<T> type, final Long searchKey1);


    public  <T extends NotificationEventBase> List<NotificationEvent<T>> getFutureNotificationFromTransactionForSearchKey1(final Class<T> type, final Long searchKey1, final NotificationSqlDao transactionalDao);


    public  <T extends NotificationEventBase> List<NotificationEvent<T>> getFutureNotificationForSearchKey2(final Class<T> type, final Long searchKey2);


    public  <T extends NotificationEventBase> List<NotificationEvent<T>> getFutureNotificationFromTransactionForSearchKey2(final Class<T> type, final Long searchKey2, final NotificationSqlDao transactionalDao);



    public void removeNotification(final Long recordId);

    public void removeNotificationFromTransaction(final Transmogrifier transmogrifier,
                                                  final Long recordId);

    /**
     * @return the name of that queue
     */
    public String getFullQName();

    /**
     * @return the service name associated to that queue
     */
    public String getServiceName();

    /**
     * @return the queue name associated
     */
    public String getQueueName();

    public String getHostName();

    public NotificationQueueHandler getHandler();
}
