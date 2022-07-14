/*
 * Copyright (C) 2007 The Guava Authors
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.commons.eventbus;

import java.lang.reflect.Method;

import org.killbill.commons.utils.Preconditions;

/**
 * Context for an exception thrown by a subscriber.
 *
 * @since 16.0
 */
public class SubscriberExceptionContext {

    private final EventBus eventBus;
    private final Object event;
    private final Object subscriber;
    private final Method subscriberMethod;

    /**
     * @param eventBus         The {@link EventBus} that handled the event and the subscriber. Useful for
     *                         broadcasting a new event based on the error.
     * @param event            The event object that caused the subscriber to throw.
     * @param subscriber       The source subscriber context.
     * @param subscriberMethod the subscribed method.
     */
    SubscriberExceptionContext(final EventBus eventBus, final Object event, final Object subscriber, final Method subscriberMethod) {
        this.eventBus = Preconditions.checkNotNull(eventBus);
        this.event = Preconditions.checkNotNull(event);
        this.subscriber = Preconditions.checkNotNull(subscriber);
        this.subscriberMethod = Preconditions.checkNotNull(subscriberMethod);
    }

    /**
     * @return The {@link EventBus} that handled the event and the subscriber. Useful for broadcasting
     * a new event based on the error.
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * @return The event object that caused the subscriber to throw.
     */
    public Object getEvent() {
        return event;
    }

    /**
     * @return The object context that the subscriber was called on.
     */
    public Object getSubscriber() {
        return subscriber;
    }

    /**
     * @return The subscribed method that threw the exception.
     */
    Method getSubscriberMethod() {
        return subscriberMethod;
    }
}
