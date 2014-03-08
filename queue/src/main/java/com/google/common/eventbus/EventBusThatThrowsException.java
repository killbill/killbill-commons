/*
 * Copyright 2010-2014 Ning, Inc.
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

package com.google.common.eventbus;

import com.google.common.collect.SetMultimap;
import org.killbill.bus.api.BusEvent;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Bus exceptions in the guava framework are swallowed  and that sucks.
 * <p/>
 * I sumitted a ticket https://code.google.com/p/guava-libraries/issues/detail?id=981 which was then merged into
 * https://code.google.com/p/guava-libraries/issues/detail?id=780
 * <p/>
 * They closed the bug, but i am still not seeing any way to get those exceptions back, so since we REALLY
 * need it, we have to hack the code:
 * <p/>
 * There is a new postWithException method that will throw an EventBusException if there is any exceptions during
 * the dispatch phase, which means that some handlers might have been called successfully and others not, which is
 * fine with our 'at least once' delivery semantics.
 */
public class EventBusThatThrowsException extends EventBus {

    public EventBusThatThrowsException(String identifier) {
        super(identifier);
    }

    public void postWithException(Object event) throws EventBusException {
        Set<Class<?>> dispatchTypes = flattenHierarchy(event.getClass());

        boolean dispatched = false;
        for (Class<?> eventType : dispatchTypes) {
            getHandlersByTypeLock().readLock().lock();
            try {
                Set<EventHandler> wrappers = getHandlersByType().get(eventType);

                if (!wrappers.isEmpty()) {
                    dispatched = true;
                    for (EventHandler wrapper : wrappers) {
                        enqueueEvent(event, wrapper);
                    }
                }
            } finally {
                getHandlersByTypeLock().readLock().unlock();
            }
        }

        if (!dispatched && !(event instanceof DeadEvent)) {
            post(new DeadEvent(this, event));
        }

        dispatchQueuedEventsWithException();
    }

    void dispatchQueuedEventsWithException() throws EventBusException {
        // don't dispatch if we're already dispatching, that would allow reentrancy
        // and out-of-order events. Instead, leave the events to be dispatched
        // after the in-progress dispatch is complete.
        if (getIsDispatching().get()) {
            return;
        }

        getIsDispatching().set(true);
        try {
            Queue<EventWithHandler> events = getEventsToDispatch().get();
            EventWithHandler eventWithHandler;
            while ((eventWithHandler = events.poll()) != null) {
                dispatchWithException(eventWithHandler.event, eventWithHandler.handler);
            }
        } finally {
            getIsDispatching().remove();
            getEventsToDispatch().remove();
        }
    }

    void dispatchWithException(Object event, EventHandler wrapper) throws EventBusException {
        try {
            wrapper.handleEvent(event);
        } catch (InvocationTargetException e) {
            throw new EventBusException(e);
        }
    }


    //
    // Even more ugliness to access private fields from the BusEvent class. Obviously this is very fragile;
    // if they decide to just rename their field name field, all hell breaks loose. So updating guava will
    //  require some attention-- but none of our tests would work so we should also see it pretty quick!
    //
    private ReadWriteLock getHandlersByTypeLock() throws EventBusException {
        return getDeclaredField("handlersByTypeLock");
    }

    private SetMultimap<Class<?>, EventHandler> getHandlersByType() throws EventBusException {
        return getDeclaredField("handlersByType");
    }

    private ThreadLocal<Boolean> getIsDispatching() throws EventBusException {
        return getDeclaredField("isDispatching");
    }

    private ThreadLocal<Queue<EventWithHandler>> getEventsToDispatch() throws EventBusException {
        return getDeclaredField("eventsToDispatch");
    }

    private <T> T getDeclaredField(final String fieldName) throws EventBusException {
        try {
            final Field f = EventBus.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (T) f.get(this);
        } catch (NoSuchFieldException e) {
            throw new EventBusException("Failed to retrieve private field from BusEvent class " + fieldName, e);
        } catch (IllegalAccessException e) {
            throw new EventBusException("Failed to retrieve private field from BusEvent class " + fieldName, e);
        }
    }
}
