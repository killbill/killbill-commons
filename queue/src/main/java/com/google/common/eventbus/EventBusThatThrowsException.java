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
import java.util.Iterator;
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
 *
 * NOTE that starting in guava-16.0 there is a new mechanism where one can register a `SubscriberExceptionHandler`, but
 * that does not quite fit our needs because we are processing events in a loop and the context of that loop needs to be taken
 * into consideration to know how to handle the exception, so we are keeping the hack below.
 *
 */
public class EventBusThatThrowsException extends EventBus {

    public EventBusThatThrowsException(String identifier) {
        super(identifier);
    }

    public void postWithException(Object event) throws EventBusException {
        Set dispatchTypes = this.flattenHierarchy(event.getClass());
        boolean dispatched = false;
        Iterator i$ = dispatchTypes.iterator();

        while(i$.hasNext()) {
            Class eventType = (Class)i$.next();
            getSubscribersByTypeLock().readLock().lock();

            try {
                Set wrappers = getSubscribersByType().get(eventType);
                if(!wrappers.isEmpty()) {
                    dispatched = true;
                    Iterator i$1 = wrappers.iterator();

                    while(i$1.hasNext()) {
                        EventSubscriber wrapper = (EventSubscriber)i$1.next();
                        this.enqueueEvent(event, wrapper);
                    }
                }
            } finally {
                getSubscribersByTypeLock().readLock().unlock();
            }
        }

        if(!dispatched && !(event instanceof DeadEvent)) {
            this.post(new DeadEvent(this, event));
        }

        dispatchQueuedEventsWithException();
    }

    void dispatchQueuedEventsWithException() throws EventBusException {
        if(!((Boolean)getIsDispatching().get()).booleanValue()) {
            getIsDispatching().set(Boolean.valueOf(true));

            try {
                Queue events = (Queue)getEventsToDispatch().get();

                EventBus.EventWithSubscriber eventWithSubscriber;
                while((eventWithSubscriber = (EventBus.EventWithSubscriber)events.poll()) != null) {
                    dispatchWithException(eventWithSubscriber.event, eventWithSubscriber.subscriber);
                }
            } finally {
                getIsDispatching().remove();
                getIsDispatching().remove();
            }

        }
    }

    void dispatchWithException(Object event, EventSubscriber wrapper) throws EventBusException {
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
    private ReadWriteLock getSubscribersByTypeLock() throws EventBusException {
        return getDeclaredField("subscribersByTypeLock");
    }

    private SetMultimap<Class<?>, EventSubscriber> getSubscribersByType() throws EventBusException {
        return getDeclaredField("subscribersByType");
    }

    private ThreadLocal<Boolean> getIsDispatching() throws EventBusException {
        return getDeclaredField("isDispatching");
    }

    private ThreadLocal<Queue<EventBus.EventWithSubscriber>> getEventsToDispatch() throws EventBusException {
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
