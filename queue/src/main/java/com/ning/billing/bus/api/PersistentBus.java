/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.bus.api;

import java.util.UUID;

import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.queue.api.QueueLifecycle;

public interface PersistentBus extends QueueLifecycle {


    public static final String EVENT_BUS_GROUP_NAME = "bus-grp";
    public static final String EVENT_BUS_TH_NAME = "bus-th";

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
     *
     * @param event to be posted
     * @throws EventBusException if bus not been started yet
     */
    public void post(BusEvent event) throws EventBusException;

    /**
     * Post an event from within a transaction.
     * Guarantees that the event is persisted on disk from within the same transaction
     *
     *
     * @param event to be posted
     * @throws EventBusException if bus not been started yet
     */
    public void postFromTransaction(BusEvent event, final Transmogrifier transmogrifier) throws EventBusException;


}
