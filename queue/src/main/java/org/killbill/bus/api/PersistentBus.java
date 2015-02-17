/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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
import java.util.List;

import org.killbill.queue.api.QueueLifecycle;

public interface PersistentBus extends QueueLifecycle {

    public static final String EVENT_BUS_GROUP_NAME = "bus-grp";

    public static final String EVENT_BUS_SERVICE = "bus-service";
    public static final String EVENT_BUS_IDENTIFIER = EVENT_BUS_SERVICE;


    public class EventBusException extends Exception {

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
     * Start accepting events and dispatching them
     */
    public void start();

    /**
     * Stop accepting events and flush event queue before it returns.
     */
    public void stop();

    /**
     * Registers all handler methods on {@code object} to receive events.
     * Handler methods need to be Annotated with {@link com.google.common.eventbus.Subscribe}
     *
     * @param handlerInstance handler to register
     * @throws EventBusException if bus not been started yet
     */
    public void register(Object handlerInstance) throws EventBusException;

    /**
     * Unregister the handler for a particular type of event
     *
     * @param handlerInstance handler to unregister
     * @throws EventBusException
     */
    public void unregister(Object handlerInstance) throws EventBusException;

    /**
     * Post an event asynchronously
     *
     * @param event to be posted
     * @throws EventBusException if bus not been started yet
     */
    public void post(BusEvent event) throws EventBusException;

    /**
     * Post an event from within a transaction.
     * Guarantees that the event is persisted on disk from within the same transaction
     *
     * @param event      to be posted
     * @param connection current connection
     * @throws EventBusException if bus not been started yet
     */
    public void postFromTransaction(BusEvent event, Connection connection) throws EventBusException;

    /**
     * Retrieve all available bus events matching that search key
     *
     * @param searchKey1 the value for key1
     * @param searchKey2 the value for key2
     * @return a list of BusEventWithMetadata objects matching the search
     */
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableBusEventsForSearchKeys(Long searchKey1, Long searchKey2);

    /**
     * Retrieve all available bus events matching that search key
     *
     * @param searchKey1 the value for key1
     * @param searchKey2 the value for key2
     * @param connection the transaction that should be used to make that search
     * @return a list of BusEventWithMetadata objects matching the search
     */
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableBusEventsFromTransactionForSearchKeys(Long searchKey1, Long searchKey2, Connection connection);

    /**
     * Retrieve all available bus events matching that search key
     *
     * @param searchKey2 the value for key2
     * @return a list of BusEventWithMetadata objects matching the search
     */
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableBusEventsForSearchKey2(Long searchKey2);

    /**
     * Retrieve all available bus events matching that search key
     *
     * @param searchKey2 the value for key2
     * @param connection the transaction that should be used to make that search
     * @return a list of BusEventWithMetadata objects matching the search
     */
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableBusEventsFromTransactionForSearchKey2(Long searchKey2, Connection connection);

    /**
     * @return the bus events that have been claimed and are being processed
     */
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getInProcessingBusEvents();

    /**
     * Retrieve all available or in processing bus events matching that search key
     *
     * @param searchKey1 the value for key1
     * @param searchKey2 the value for key2
     * @return a list of BusEventWithMetadata objects matching the search
     */
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsForSearchKeys(Long searchKey1, Long searchKey2);

    /**
     * Retrieve all available or in processing bus events matching that search key
     *
     * @param searchKey1 the value for key1
     * @param searchKey2 the value for key2
     * @param connection the transaction that should be used to make that search
     * @return a list of BusEventWithMetadata objects matching the search
     */
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsFromTransactionForSearchKeys(Long searchKey1, Long searchKey2, Connection connection);

    /**
     * Retrieve all available or in processing bus events matching that search key
     *
     * @param searchKey2 the value for key2
     * @return a list of BusEventWithMetadata objects matching the search
     */
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsForSearchKey2(Long searchKey2);

    /**
     * Retrieve all available or in processing bus events matching that search key
     *
     * @param searchKey2 the value for key2
     * @param connection the transaction that should be used to make that search
     * @return a list of BusEventWithMetadata objects matching the search
     */
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsFromTransactionForSearchKey2(Long searchKey2, Connection connection);
}
