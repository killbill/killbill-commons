/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Bus exceptions in the guava framework are swallowed and that sucks.
 * <p/>
 * I sumitted a ticket https://code.google.com/p/guava-libraries/issues/detail?id=981 which was then merged into
 * https://code.google.com/p/guava-libraries/issues/detail?id=780
 * <p/>
 * They closed the bug, but i am still not seeing any way to get those exceptions back, so since we REALLY
 * need it, we have to hack the code.
 */
public class EventBusThatThrowsException extends EventBus {

    @VisibleForTesting
    final SubscriberExceptionsTrackerHandler exceptionHandler;

    private final SubscriberRegistry subscribers;
    private final Dispatcher dispatcher;

    public EventBusThatThrowsException(final String identifier) {
        super(identifier,
              // See postWithException below
              MoreExecutors.directExecutor(),
              Dispatcher.immediate(),
              new SubscriberExceptionsTrackerHandler());
        this.subscribers = getSubscribers();
        this.dispatcher = getDispatcher();
        this.exceptionHandler = getExceptionHandler();
    }

    public void postWithException(final Object event) throws EventBusException {
        final Iterator<Subscriber> eventSubscribers = subscribers.getSubscribers(event);
        if (eventSubscribers.hasNext()) {
            RuntimeException guavaException = null;
            final Exception subscriberException;
            try {
                dispatcher.dispatch(event, eventSubscribers);
            } catch (final RuntimeException e) {
                guavaException = e;
            } finally {
                // This works because we are both using the immediate dispatcher and the direct executor
                // Note: we always want to dequeue here to avoid any memory leaks
                subscriberException = exceptionHandler.caught();
            }

            if (guavaException != null) {
                throw guavaException;
            }
            if (subscriberException != null) {
                throw new EventBusException(subscriberException);
            }
        } else if (!(event instanceof DeadEvent)) {
            // the event had no subscribers and was not itself a DeadEvent
            post(new DeadEvent(this, event));
        }
    }

    //
    // Even more ugliness to access private fields from the BusEvent class. Obviously this is very fragile;
    // if they decide to just rename their field name field, all hell breaks loose. So updating guava will
    //  require some attention-- but none of our tests would work so we should also see it pretty quick!
    //

    private SubscriberRegistry getSubscribers() {
        return getDeclaredField("subscribers");
    }

    private Dispatcher getDispatcher() {
        return getDeclaredField("dispatcher");
    }

    private SubscriberExceptionsTrackerHandler getExceptionHandler() {
        return getDeclaredField("exceptionHandler");
    }

    private <T> T getDeclaredField(final String fieldName) {
        try {
            final Field f = EventBus.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (T) f.get(this);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException("Failed to retrieve private field from BusEvent class " + fieldName, e);
        } catch (final IllegalAccessException e) {
            throw new RuntimeException("Failed to retrieve private field from BusEvent class " + fieldName, e);
        }
    }

    static final class SubscriberExceptionsTrackerHandler implements SubscriberExceptionHandler {

        @VisibleForTesting
        final ThreadLocal<Exception> lastException = new ThreadLocal<Exception>() {};

        private final SubscriberExceptionHandler loggerHandler = LoggingHandler.INSTANCE;

        @Override
        public void handleException(final Throwable exception, final SubscriberExceptionContext context) {
            // By convention, re-throw the very first exception
            if (lastException.get() == null) {
                // Wrapping for legacy reasons
                lastException.set(new InvocationTargetException(exception));
            }

            loggerHandler.handleException(exception, context);
        }

        Exception caught() {
            final Exception exception = lastException.get();
            lastException.set(null);
            return exception;
        }
    }
}
