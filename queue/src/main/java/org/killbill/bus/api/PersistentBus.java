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

package org.killbill.bus.api;

import java.sql.Connection;

import org.joda.time.DateTime;
import org.killbill.common.eventbus.Subscribe;
import org.killbill.queue.api.QueueLifecycle;

/**
 * When an Iterable is returned, the client must iterate through all results to close the DB connection.
 */
public interface PersistentBus extends QueueLifecycle {

    String EVENT_BUS_GROUP_NAME = "bus-grp";

    String EVENT_BUS_SERVICE = "bus-service";
    String EVENT_BUS_IDENTIFIER = EVENT_BUS_SERVICE;


    class EventBusException extends Exception {

        private static final long serialVersionUID = 12355236L;

        public EventBusException() {
            super();
        }

        public EventBusException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public EventBusException(final String message) {
            super(message);
        }
    }


    /**
     * Registers all handler methods on {@code object} to receive events.
     * Handler methods need to be Annotated with {@link Subscribe}
     *
     * @param handlerInstance handler to register
     * @throws EventBusException if bus not been started yet
     */
    void register(Object handlerInstance) throws EventBusException;

    /**
     * Unregister the handler for a particular type of event
     *
     * @param handlerInstance handler to unregister
     * @throws EventBusException
     */
    void unregister(Object handlerInstance) throws EventBusException;

    /**
     * Post an event asynchronously
     *
     * @param event to be posted
     * @throws EventBusException if bus not been started yet
     */
    void post(BusEvent event) throws EventBusException;

    /**
     * Post an event from within a transaction.
     * Guarantees that the event is persisted on disk from within the same transaction
     *
     * @param event      to be posted
     * @param connection current connection
     * @throws EventBusException if bus not been started yet
     */
    void postFromTransaction(BusEvent event, Connection connection) throws EventBusException;

    /**
     * Retrieve all available bus events matching that search key
     *
     * @param searchKey1 the value for key1
     * @param searchKey2 the value for key2
     * @return a list of BusEventWithMetadata objects matching the search
     */
    <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableBusEventsForSearchKeys(Long searchKey1, Long searchKey2);

    /**
     * Retrieve all available bus events matching that search key
     *
     * @param searchKey1 the value for key1
     * @param searchKey2 the value for key2
     * @param connection the transaction that should be used to make that search
     * @return a list of BusEventWithMetadata objects matching the search
     */
    <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableBusEventsFromTransactionForSearchKeys(Long searchKey1, Long searchKey2, Connection connection);

    /**
     * Retrieve all available bus events matching that search key
     *
     * @param maxCreatedDate created_date cutoff, to limit the search
     * @param searchKey2 the value for key2
     * @return a list of BusEventWithMetadata objects matching the search
     */
    <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableBusEventsForSearchKey2(DateTime maxCreatedDate, Long searchKey2);

    /**
     * Retrieve all available bus events matching that search key
     *
     * @param maxCreatedDate created_date cutoff, to limit the search
     * @param searchKey2 the value for key2
     * @param connection the transaction that should be used to make that search
     * @return a list of BusEventWithMetadata objects matching the search
     */
    <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableBusEventsFromTransactionForSearchKey2(DateTime maxCreatedDate, Long searchKey2, Connection connection);

    /**
     * @return the bus events that have been claimed and are being processed
     */
    <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getInProcessingBusEvents();

    /**
     * Retrieve all available or in processing bus events matching that search key
     *
     * @param searchKey1 the value for key1
     * @param searchKey2 the value for key2
     * @return a list of BusEventWithMetadata objects matching the search
     */
    <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsForSearchKeys(Long searchKey1, Long searchKey2);

    /**
     * Retrieve all available or in processing bus events matching that search key
     *
     * @param searchKey1 the value for key1
     * @param searchKey2 the value for key2
     * @param connection the transaction that should be used to make that search
     * @return a list of BusEventWithMetadata objects matching the search
     */
    <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsFromTransactionForSearchKeys(Long searchKey1, Long searchKey2, Connection connection);

    /**
     * Retrieve all available or in processing bus events matching that search key
     *
     * @param maxCreatedDate created_date cutoff, to limit the search
     * @param searchKey2 the value for key2
     * @return a list of BusEventWithMetadata objects matching the search
     */
    <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsForSearchKey2(DateTime maxCreatedDate, Long searchKey2);

    /**
     * Retrieve all available or in processing bus events matching that search key
     *
     * @param maxCreatedDate created_date cutoff, to limit the search
     * @param searchKey2 the value for key2
     * @param connection the transaction that should be used to make that search
     * @return a list of BusEventWithMetadata objects matching the search
     */
    <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsFromTransactionForSearchKey2(DateTime maxCreatedDate, Long searchKey2, Connection connection);

    /**
     * Retrieve all historical bus events matching that search key
     *
     * @param searchKey1 the value for key1
     * @param searchKey2 the value for key2
     * @return a list of BusEventWithMetadata objects matching the search
     */
    <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getHistoricalBusEventsForSearchKeys(Long searchKey1, Long searchKey2);

    /**
     * Retrieve all historical bus events matching that search key
     *
     * @param minCreatedDate created_date cutoff, to limit the search
     * @param searchKey2     the value for key2
     * @return a list of BusEventWithMetadata objects matching the search
     */
    <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getHistoricalBusEventsForSearchKey2(DateTime minCreatedDate, Long searchKey2);

    /**
     * Count the number of bus entries ready to be processed
     *
     * @param maxCreatedDate created_date cutoff (typically now())
     * @return the number of ready entries
     */
    long getNbReadyEntries(final DateTime maxCreatedDate);
}
