/*
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

package org.killbill.common.eventbus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test case for {@link org.killbill.common.eventbus.EventBus}.
 *
 * @author Cliff Biffle
 */
public class TestEventBus {
    private static final String EVENT = "Hello";
    private static final String BUS_IDENTIFIER = "test-bus";

    private org.killbill.common.eventbus.EventBus bus;

    @BeforeMethod
    protected void setUp() {
        bus = new org.killbill.common.eventbus.EventBus(BUS_IDENTIFIER);
    }

    @Test
    public void testBasicCatcherDistribution() {
        final StringCatcher catcher = new StringCatcher();
        bus.register(catcher);
        bus.post(EVENT);

        final List<String> events = catcher.getEvents();
        Assert.assertEquals(events.size(), 1, "Only one event should be delivered.");
        Assert.assertEquals(events.get(0), EVENT, "Correct string should be delivered.");
    }

    /**
     * Tests that events are distributed to any subscribers to their type or any supertype, including
     * interfaces and superclasses.
     *
     * <p>Also checks delivery ordering in such cases.
     */
    @Test
    public void testPolymorphicDistribution() {
        // Three catchers for related types String, Object, and Comparable<?>.
        // String isa Object
        // String isa Comparable<?>
        // Comparable<?> isa Object
        final StringCatcher stringCatcher = new StringCatcher();

        final List<Object> objectEvents = new ArrayList<>();
        final Object objCatcher =
                new Object() {
                    @SuppressWarnings("unused")
                    @org.killbill.common.eventbus.Subscribe
                    public void eat(final Object food) {
                        objectEvents.add(food);
                    }
                };

        final List<Comparable<?>> compEvents = new ArrayList<>();
        final Object compCatcher =
                new Object() {
                    @SuppressWarnings("unused")
                    @org.killbill.common.eventbus.Subscribe
                    public void eat(final Comparable<?> food) {
                        compEvents.add(food);
                    }
                };
        bus.register(stringCatcher);
        bus.register(objCatcher);
        bus.register(compCatcher);

        // Two additional event types: Object and Comparable<?> (played by Integer)
        final Object objEvent = new Object();
        final Object compEvent = 6;

        bus.post(EVENT);
        bus.post(objEvent);
        bus.post(compEvent);

        // Check the StringCatcher...
        final List<String> stringEvents = stringCatcher.getEvents();
        Assert.assertEquals(stringEvents.size(), 1, "Only one String should be delivered.");
        Assert.assertEquals(stringEvents.get(0), EVENT, "Correct string should be delivered.");

        // Check the Catcher<Object>...
        Assert.assertEquals(objectEvents.size(), 3, "Three Objects should be delivered.");
        Assert.assertEquals(objectEvents.get(0), EVENT, "String fixture must be first object delivered.");
        Assert.assertEquals(objectEvents.get(1), objEvent, "Object fixture must be second object delivered.");
        Assert.assertEquals(objectEvents.get(2), compEvent, "Comparable fixture must be third object delivered.");

        // Check the Catcher<Comparable<?>>...
        Assert.assertEquals(compEvents.size(), 2, "Two Comparable<?>s should be delivered.");
        Assert.assertEquals(compEvents.get(0), EVENT, "String fixture must be first comparable delivered.");
        Assert.assertEquals(compEvents.get(1), compEvent, "Comparable fixture must be second comparable delivered.");
    }

    @Test
    public void testSubscriberThrowsException() throws Exception {
        final RecordingSubscriberExceptionHandler handler = new RecordingSubscriberExceptionHandler();
        final org.killbill.common.eventbus.EventBus eventBus = new org.killbill.common.eventbus.EventBus(handler);
        final RuntimeException exception =
                new RuntimeException("but culottes have a tendency to ride up!");
        final Object subscriber =
                new Object() {
                    @org.killbill.common.eventbus.Subscribe
                    public void throwExceptionOn(final String ignored) {
                        throw exception;
                    }
                };
        eventBus.register(subscriber);
        eventBus.post(EVENT);

        Assert.assertEquals(handler.exception, exception, "Cause should be available.");
        Assert.assertEquals(handler.context.getEventBus(), eventBus, "EventBus should be available.");
        Assert.assertEquals(handler.context.getEvent(), EVENT, "Event should be available.");
        Assert.assertEquals(handler.context.getSubscriber(), subscriber, "Subscriber should be available.");
        Assert.assertEquals(handler.context.getSubscriberMethod(),
                            subscriber.getClass().getMethod("throwExceptionOn", String.class),
                            "Method should be available.");
    }

    @Test
    public void testSubscriberThrowsExceptionHandlerThrowsException() {
        final org.killbill.common.eventbus.EventBus eventBus = new org.killbill.common.eventbus.EventBus(
                new org.killbill.common.eventbus.SubscriberExceptionHandler() {
                    @Override
                    public void handleException(final Throwable exception, final org.killbill.common.eventbus.SubscriberExceptionContext context) {
                        throw new RuntimeException();
                    }
                });
        final Object subscriber = new Object() {
            @org.killbill.common.eventbus.Subscribe
            public void throwExceptionOn(final String ignored) {
                throw new RuntimeException();
            }
        };
        eventBus.register(subscriber);
        try {
            eventBus.post(EVENT);
        } catch (final RuntimeException e) {
            Assert.fail("Exception should not be thrown: " + e);
        }
    }

    @Test
    public void testDeadEventForwarding() {
        final GhostCatcher catcher = new GhostCatcher();
        bus.register(catcher);

        // A String -- an event for which noone has registered.
        bus.post(EVENT);

        final List<org.killbill.common.eventbus.DeadEvent> events = catcher.getEvents();
        Assert.assertEquals(events.size(), 1, "One dead event should be delivered.");
        Assert.assertEquals(events.get(0).getEvent(), EVENT, "The dead event should wrap the original event.");
    }

    @Test
    public void testDeadEventPosting() {
        final GhostCatcher catcher = new GhostCatcher();
        bus.register(catcher);

        bus.post(new org.killbill.common.eventbus.DeadEvent(this, EVENT));

        final List<org.killbill.common.eventbus.DeadEvent> events = catcher.getEvents();
        Assert.assertEquals(events.size(), 1, "The explicit DeadEvent should be delivered.");
        Assert.assertEquals(events.get(0).getEvent(), EVENT, "The dead event must not be re-wrapped.");
    }

    @Test
    public void testMissingSubscribe() {
        bus.register(new Object());
    }

    @Test
    public void testUnregister() {
        final StringCatcher catcher1 = new StringCatcher();
        final StringCatcher catcher2 = new StringCatcher();
        try {
            bus.unregister(catcher1);
            Assert.fail("Attempting to unregister an unregistered object succeeded");
        } catch (final IllegalArgumentException expected) {
            // OK.
        }

        bus.register(catcher1);
        bus.post(EVENT);
        bus.register(catcher2);
        bus.post(EVENT);

        final Collection<String> expectedEvents = new ArrayList<>();
        expectedEvents.add(EVENT);
        expectedEvents.add(EVENT);

        Assert.assertEquals(catcher1.getEvents(), expectedEvents, "Two correct events should be delivered.");

        Assert.assertEquals(catcher2.getEvents(), List.of(EVENT), "One correct event should be delivered.");

        bus.unregister(catcher1);
        bus.post(EVENT);

        Assert.assertEquals(catcher1.getEvents(), expectedEvents, "Shouldn't catch any more events when unregistered.");
        Assert.assertEquals(catcher2.getEvents(), expectedEvents, "Two correct events should be delivered.");

        try {
            bus.unregister(catcher1);
            Assert.fail("Attempting to unregister an unregistered object succeeded");
        } catch (final IllegalArgumentException expected) {
            // OK.
        }

        bus.unregister(catcher2);
        bus.post(EVENT);

        Assert.assertEquals(catcher1.getEvents(), expectedEvents, "Shouldn't catch any more events when unregistered.");
        Assert.assertEquals(catcher2.getEvents(), expectedEvents, "Shouldn't catch any more events when unregistered.");
    }

    // NOTE: This test will always pass if register() is thread-safe but may also
    // pass if it isn't, though this is unlikely.
    @Test
    public void testRegisterThreadSafety() throws Exception {
        final List<StringCatcher> catchers = new CopyOnWriteArrayList<>();
        final List<Future<?>> futures = new ArrayList<>();
        final ExecutorService executor = Executors.newFixedThreadPool(10);
        final int numberOfCatchers = 10000;
        for (int i = 0; i < numberOfCatchers; i++) {
            futures.add(executor.submit(new Registrator(bus, catchers)));
        }
        for (int i = 0; i < numberOfCatchers; i++) {
            futures.get(i).get();
        }
        Assert.assertEquals(catchers.size(), numberOfCatchers, "Unexpected number of catchers in the list");

        bus.post(EVENT);
        final List<String> expectedEvents = List.of(EVENT);
        for (final StringCatcher catcher : catchers) {
            Assert.assertEquals(catcher.getEvents(), expectedEvents, "One of the registered catchers did not receive an event.");
        }
    }

    @Test
    public void testToString() {
        final org.killbill.common.eventbus.EventBus eventBus = new org.killbill.common.eventbus.EventBus("a b ; - \" < > / \\ €");
        Assert.assertEquals(eventBus.toString(), "EventBus {identifier='a b ; - \" < > / \\ €'}");
    }

    /**
     * Tests that bridge methods are not subscribed to events. In Java 8, annotations are included on
     * the bridge method in addition to the original method, which causes both the original and bridge
     * methods to be subscribed (since both are annotated @Subscribe) without specifically checking
     * for bridge methods.
     */
    @Test
    public void testRegistrationWithBridgeMethod() {
        final AtomicInteger calls = new AtomicInteger();
        bus.register(
                new Callback<String>() {
                    @org.killbill.common.eventbus.Subscribe
                    @Override
                    public void call(final String s) {
                        calls.incrementAndGet();
                    }
                });

        bus.post("hello");

        Assert.assertEquals(1, calls.get());
    }

    @Test
    public void testPrimitiveSubscribeFails() {
        class SubscribesToPrimitive {
            @org.killbill.common.eventbus.Subscribe
            public void toInt(final int ignored) {}
        }
        try {
            bus.register(new SubscribesToPrimitive());
            Assert.fail("should have thrown");
        } catch (final IllegalArgumentException expected) {
        }
    }

    /** Records thrown exception information. */
    private static final class RecordingSubscriberExceptionHandler
            implements SubscriberExceptionHandler {

        public org.killbill.common.eventbus.SubscriberExceptionContext context;
        public Throwable exception;

        @Override
        public void handleException(final Throwable exception, final SubscriberExceptionContext context) {
            this.exception = exception;
            this.context = context;
        }
    }

    /** Runnable which registers a StringCatcher on an event bus and adds it to a list. */
    private static class Registrator implements Runnable {
        private final org.killbill.common.eventbus.EventBus bus;
        private final List<StringCatcher> catchers;

        Registrator(final EventBus bus, final List<StringCatcher> catchers) {
            this.bus = bus;
            this.catchers = catchers;
        }

        @Override
        public void run() {
            final StringCatcher catcher = new StringCatcher();
            bus.register(catcher);
            catchers.add(catcher);
        }
    }

    /**
     * A collector for DeadEvents.
     *
     * @author cbiffle
     */
    public static class GhostCatcher {
        private final List<org.killbill.common.eventbus.DeadEvent> events = new ArrayList<>();

        @Subscribe
        public void ohNoesIHaveDied(final org.killbill.common.eventbus.DeadEvent event) {
            events.add(event);
        }

        public List<DeadEvent> getEvents() {
            return events;
        }
    }

    private interface Callback<T> {
        void call(T t);
    }
}
