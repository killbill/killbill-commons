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

package org.killbill.bus;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.BusEventWithMetadata;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBusConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBusThatThrowsException;

public class InMemoryPersistentBus implements PersistentBus {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPersistentBus.class);

    private final EventBusDelegate delegate;
    private final AtomicBoolean isInitialized;

    @Override
    public boolean startQueue() {
        return true;
    }

    @Override
    public void stopQueue() {
    }

    @Override
    public boolean isStarted() {
        return true;
    }

    public class EventBusDelegate extends EventBusThatThrowsException {

        public EventBusDelegate() {
            super("Bus");
        }

        public void completeDispatch() {
        }

        // No way to really 'stop' an executor; what we do is:
        // i) disallow any new events into the queue
        // ii) empty the queue
        //
        // That will only work if the event submitter handles EventBusException correctly when posting.
        //
        public void stop() {
        }
    }

    @Inject
    public InMemoryPersistentBus(final PersistentBusConfig config) {

        final ThreadGroup group = new ThreadGroup(EVENT_BUS_GROUP_NAME);

        this.delegate = new EventBusDelegate();
        this.isInitialized = new AtomicBoolean(false);
    }

    @Override
    public void register(final Object handlerInstance) throws EventBusException {
        checkInitialized("register");
        delegate.register(handlerInstance);
    }

    @Override
    public void unregister(final Object handlerInstance) throws EventBusException {
        checkInitialized("unregister");
        delegate.unregister(handlerInstance);
    }

    @Override
    public void post(final BusEvent event) throws EventBusException {
        checkInitialized("post");
        try {
            delegate.postWithException(event);
        } catch (final com.google.common.eventbus.EventBusException e) {
            throw new EventBusException(e.getMessage(), e);
        }
    }

    @Override
    public void postFromTransaction(final BusEvent event, final Connection connection) throws EventBusException {
        checkInitialized("postFromTransaction");
        try {
            delegate.postWithException(event);
        } catch (final com.google.common.eventbus.EventBusException e) {
            throw new EventBusException(e.getMessage(), e);
        }
    }

    @Override
    public void start() {
        if (isInitialized.compareAndSet(false, true)) {
            log.info("InMemoryPersistentBus started...");

        }
    }

    private void checkInitialized(final String operation) throws EventBusException {
        if (!isInitialized.get()) {
            throw new EventBusException(String.format("Attempting operation %s on an non initialized bus",
                    operation));
        }
    }

    @Override
    public void stop() {
        if (isInitialized.compareAndSet(true, false)) {
            log.info("InMemoryPersistentBus stopping...");
            delegate.completeDispatch();
            delegate.stop();
            log.info("InMemoryPersistentBus stopped...");
        }
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableBusEventsForSearchKeys(final Long searchKey1, final Long searchKey2) {
        throw new UnsupportedOperationException("Guava doesn't expose the events to dispatch");
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableBusEventsFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        throw new UnsupportedOperationException("Guava doesn't expose the events to dispatch");
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableBusEventsForSearchKey2(final DateTime maxCreatedDate, final Long searchKey2) {
        throw new UnsupportedOperationException("Guava doesn't expose the events to dispatch");
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableBusEventsFromTransactionForSearchKey2(final DateTime maxCreatedDate, final Long searchKey2, final Connection connection) {
        throw new UnsupportedOperationException("Guava doesn't expose the events to dispatch");
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getInProcessingBusEvents() {
        throw new UnsupportedOperationException("Guava doesn't expose the events to dispatch");
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsForSearchKeys(final Long searchKey1, final Long searchKey2) {
        throw new UnsupportedOperationException("Guava doesn't expose the events to dispatch");
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        throw new UnsupportedOperationException("Guava doesn't expose the events to dispatch");
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsForSearchKey2(final DateTime maxCreatedDate, final Long searchKey2) {
        throw new UnsupportedOperationException("Guava doesn't expose the events to dispatch");
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsFromTransactionForSearchKey2(final DateTime maxCreatedDate, final Long searchKey2, final Connection connection) {
        throw new UnsupportedOperationException("Guava doesn't expose the events to dispatch");
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getHistoricalBusEventsForSearchKeys(final Long searchKey1, final Long searchKey2) {
        throw new UnsupportedOperationException("Guava doesn't expose the events to dispatch");
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getHistoricalBusEventsForSearchKey2(final DateTime minCreatedDate, final Long searchKey2) {
        throw new UnsupportedOperationException("Guava doesn't expose the events to dispatch");
    }
}
