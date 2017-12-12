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

package org.killbill.notificationq.api;

import java.io.IOException;
import java.sql.Connection;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.killbill.queue.api.QueueLifecycle;

/**
 * A NotificationQueue offers a persistent queue with a set of API to record future notifications along with their callbacks.
 *
 * When an Iterable is returned, the client must iterate through all results to close the DB connection.
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
     * @param futureNotificationTime the time at which the notification is ready
     * @param eventJson              the event to be serailzed on disk
     * @param userToken              a opaque token that can be attached to that event
     * @param searchKey1             a key that can be used for search
     * @param searchKey2             a key that can be used for search
     * @throws IOException if the serialization of the event fails
     */
    void recordFutureNotification(final DateTime futureNotificationTime,
                                  final NotificationEvent eventJson,
                                  final UUID userToken,
                                  final Long searchKey1,
                                  final Long searchKey2)
            throws IOException;

    /**
     * @param connection             the transaction that should be used to record the event
     * @param futureNotificationTime the time at which the notification is ready
     * @param eventJson              the event to be serailzed on disk
     * @param userToken              a opaque token that can be attached to that event
     * @param searchKey1             a key that can be used for search
     * @param searchKey2             a key that can be used for search
     * @throws IOException if the serialization of the event fails
     */
    void recordFutureNotificationFromTransaction(final Connection connection,
                                                 final DateTime futureNotificationTime,
                                                 final NotificationEvent eventJson,
                                                 final UUID userToken,
                                                 final Long searchKey1,
                                                 final Long searchKey2)
            throws IOException;

    /**
     * Retrieve all future notifications associated with that queue and matching that search key
     *
     * @param searchKey1 the value for key1
     * @param searchKey2 the value for key2
     * @return a list of NotificationEventWithMetadata objects matching the search
     */
    <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKeys(final Long searchKey1, final Long searchKey2);

    /**
     * Retrieve all future notifications associated with that queue and matching that search key
     *
     * @param searchKey1 the value for key1
     * @param searchKey2 the value for key2
     * @param connection the transaction that should be used to make that search
     * @return a list of NotificationEventWithMetadata objects matching the search
     */
    <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection);

    /**
     * Retrieve all future notifications associated with that queue and matching that search key
     *
     * @param maxEffectiveDate effective_date cutoff, to limit the search
     * @param searchKey2 the value for key2
     * @return a list of NotificationEventWithMetadata objects matching the search
     */
    <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureNotificationForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2);

    /**
     * Retrieve all future notifications associated with that queue and matching that search key
     *
     * @param maxEffectiveDate effective_date cutoff, to limit the search
     * @param searchKey2 the value for key2
     * @param connection the transaction that should be used to make that search
     * @return a list of NotificationEventWithMetadata objects matching the search
     */
    <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureNotificationFromTransactionForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2, final Connection connection);

    /**
     * @return the notifications that have been claimed and are being processed
     */
    <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getInProcessingNotifications();

    /**
     * Retrieve all future or in processing notifications associated with that queue and matching that search key
     *
     * @param searchKey1 the value for key1
     * @param searchKey2 the value for key2
     * @return a list of NotificationEventWithMetadata objects matching the search
     */
    <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationForSearchKeys(final Long searchKey1, final Long searchKey2);

    /**
     * Retrieve all future or in processing notifications associated with that queue and matching that search key
     *
     * @param searchKey1 the value for key1
     * @param searchKey2 the value for key2
     * @param connection the transaction that should be used to make that search
     * @return a list of NotificationEventWithMetadata objects matching the search
     */
    <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection);

    /**
     * Retrieve all future or in processing notifications associated with that queue and matching that search key
     *
     * @param maxEffectiveDate effective_date cutoff, to limit the search
     * @param searchKey2 the value for key2
     * @return a list of NotificationEventWithMetadata objects matching the search
     */
    <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2);

    /**
     * Retrieve all future or in processing notifications associated with that queue and matching that search key
     *
     * @param maxEffectiveDate effective_date cutoff, to limit the search
     * @param searchKey2 the value for key2
     * @param connection the transaction that should be used to make that search
     * @return a list of NotificationEventWithMetadata objects matching the search
     */
    <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getFutureOrInProcessingNotificationFromTransactionForSearchKey2(final DateTime maxEffectiveDate, final Long searchKey2, final Connection connection);

    /**
     * Retrieve all historical notifications associated with that queue and matching that search key
     *
     * @param searchKey1 the value for key1
     * @param searchKey2 the value for key2
     * @return a list of NotificationEventWithMetadata objects matching the search
     */
    <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getHistoricalNotificationForSearchKeys(final Long searchKey1, final Long searchKey2);

    /**
     * Retrieve all historical notifications associated with that queue and matching that search key
     *
     * @param minEffectiveDate effective_date cutoff, to limit the search
     * @param searchKey2 the value for key2
     * @return a list of NotificationEventWithMetadata objects matching the search
     */
    <T extends NotificationEvent> Iterable<NotificationEventWithMetadata<T>> getHistoricalNotificationForSearchKey2(final DateTime minEffectiveDate, final Long searchKey2);

    /**
     * Count the number of notifications ready to be processed
     *
     * @param maxEffectiveDate effective_date cutoff (typically now())
     * @return the number of ready entries
     */
    long getNbReadyEntries(final DateTime maxEffectiveDate);

    /**
     * Move the notification to history table and mark it as 'removed'
     *
     * @param recordId the recordId
     */
    void removeNotification(final Long recordId);

    void removeNotificationFromTransaction(final Connection connection,
                                           final Long recordId);

    /**
     * @return the name of that queue
     */
    String getFullQName();

    /**
     * @return the service name associated to that queue
     */
    String getServiceName();

    /**
     * @return the queue name associated
     */
    String getQueueName();

    /**
     * @return the handler associated with that notification queue
     */
    NotificationQueueHandler getHandler();
}
