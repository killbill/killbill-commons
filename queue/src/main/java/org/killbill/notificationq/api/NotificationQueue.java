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

package org.killbill.notificationq.api;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.killbill.notificationq.dao.NotificationSqlDao;
import org.killbill.queue.api.QueueLifecycle;

/**
 * A NotificationQueue offers a persistent queue with a set of API to record future notifications along with their callbacks.
 *
 * <p>
 */
public interface NotificationQueue extends QueueLifecycle {

    /**
     * Record the need to be called back when the notification is ready
     *
     * @param futureNotificationTime the time at which the notification is ready
     *
     * @param eventJson        the key for that notification
     */

    /**
     *
     * @param futureNotificationTime the time at which the notification is ready
     * @param eventJson              the event to be serailzed on disk
     * @param userToken              a opaque token that can be attached to that event
     * @param searchKey1             a key that can be used for search
     * @param searchKey2             a key that can be used for search
     * @throws IOException           if the serialization of the event fails
     */
    public void recordFutureNotification(final DateTime futureNotificationTime,
                                         final NotificationEvent eventJson,
                                         final UUID userToken,
                                         final Long searchKey1,
                                         final Long searchKey2)
            throws IOException;

    /**
     *
     * @param transmogrifier         the transaction that should be used to record the event
     * @param futureNotificationTime the time at which the notification is ready
     * @param eventJson              the event to be serailzed on disk
     * @param userToken              a opaque token that can be attached to that event
     * @param searchKey1             a key that can be used for search
     * @param searchKey2             a key that can be used for search
     * @throws IOException           if the serialization of the event fails
     */
    public void recordFutureNotificationFromTransaction(final Transmogrifier transmogrifier,
                                                        final DateTime futureNotificationTime,
                                                        final NotificationEvent eventJson,
                                                        final UUID userToken,
                                                        final Long searchKey1,
                                                        final Long searchKey2)
            throws IOException;


    /**
     *
     *  Retrieve all future notifications associated with that queue and matching that search key
     *
     * @param type       the class associated with the event
     * @param searchKey1 the value for key1
     * @param <T>        the type of event
     * @return           a list of NotificationEventWithMetadata objects matching the search
     */
    public  <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKeys(final Class<T> type, final Long searchKey1, final Long searchKey2);


    /**
     *
     *  Retrieve all future notifications associated with that queue and matching that search key
     *
     * @param type               the class associated with the event
     * @param searchKey1         the value for key1
     * @param transmogrifier     the transaction that should be used to make that search
     * @param <T>                the type of event
     * @return                   a list of NotificationEventWithMetadata objects matching the search
     */
    public  <T extends NotificationEvent> List<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKeys(final Class<T> type, final Long searchKey1, final Long searchKey2, final Transmogrifier transmogrifier);



    /**
     * Move the notification to history table and mark it as 'removed'
     *
     * @param recordId the recordId
     */
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

    /**
     *
     * @return the handler associated with that notification queue
     */
    public NotificationQueueHandler getHandler();
}
