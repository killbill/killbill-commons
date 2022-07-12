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

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for {@link Dispatcher} implementations.
 *
 * @author Colin Decker
 */
public class TestDispatcher {

    private final EventBus bus = new EventBus();

    private final IntegerSubscriber i1 = new IntegerSubscriber("i1");
    private final IntegerSubscriber i2 = new IntegerSubscriber("i2");
    private final IntegerSubscriber i3 = new IntegerSubscriber("i3");
    private final List<Subscriber> integerSubscribers = List.of(subscriber(bus, i1, "handleInteger", Integer.class),
                                                                subscriber(bus, i2, "handleInteger", Integer.class),
                                                                subscriber(bus, i3, "handleInteger", Integer.class));

    private final StringSubscriber s1 = new StringSubscriber("s1");
    private final StringSubscriber s2 = new StringSubscriber("s2");
    private final List<Subscriber> stringSubscribers = List.of(subscriber(bus, s1, "handleString", String.class),
                                                               subscriber(bus, s2, "handleString", String.class));
    private final ConcurrentLinkedQueue<Object> dispatchedSubscribers = new ConcurrentLinkedQueue<>();

    private Dispatcher dispatcher;

    @Test(groups = "fast")
    public void testPerThreadQueuedDispatcher() {
        dispatcher = Dispatcher.perThreadDispatchQueue();
        dispatcher.dispatch(1, integerSubscribers.iterator());

        Assert.assertTrue(dispatchedSubscribers.containsAll(List.of(i1, i2, i3, // Integer subscribers are dispatched to first.
                                                                    s1, s2, // Though each integer subscriber dispatches to all string subscribers,
                                                                    s1, s2, // those string subscribers aren't actually dispatched to until all integer
                                                                    s1, s2))); // subscribers have finished.
    }

    @Test(groups = "fast")
    public void testImmediateDispatcher() {
        dispatcher = Dispatcher.immediate();
        dispatcher.dispatch(1, integerSubscribers.iterator());

        Assert.assertTrue(dispatchedSubscribers.containsAll(List.of(i1, s1, s2, // Each integer subscriber immediately dispatches to 2 string subscribers.
                                                                    i2, s1, s2, i3, s1, s2)));
    }

    private static Subscriber subscriber(final EventBus bus, final Object target, final String methodName, final Class<?> eventType) {
        try {
            return Subscriber.create(bus, target, target.getClass().getMethod(methodName, eventType));
        } catch (final NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    public final class IntegerSubscriber {
        private final String name;

        public IntegerSubscriber(final String name) {
            this.name = name;
        }

        @Subscribe
        public void handleInteger(final Integer ignored) {
            dispatchedSubscribers.add(this);
            dispatcher.dispatch("hello", stringSubscribers.iterator());
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public final class StringSubscriber {
        private final String name;

        public StringSubscriber(final String name) {
            this.name = name;
        }

        @Subscribe
        public void handleString(final String ignored) {
            dispatchedSubscribers.add(this);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
