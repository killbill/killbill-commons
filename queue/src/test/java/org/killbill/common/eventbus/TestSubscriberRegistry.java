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

import java.util.Iterator;
import java.util.Set;

import org.killbill.common.eventbus.EventBus;
import org.killbill.common.eventbus.Subscribe;
import org.killbill.commons.utils.collect.Iterators;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests for {@link org.killbill.common.eventbus.SubscriberRegistry}. Taken from Guava's test.
 *
 * @author Colin Decker
 */
public class TestSubscriberRegistry {

    private org.killbill.common.eventbus.SubscriberRegistry registry;

    @BeforeMethod
    public void beforeMethod() {
        registry = new org.killbill.common.eventbus.SubscriberRegistry(new EventBus());
    }

    @Test
    public void testRegister() {
        assertEquals(registry.getSubscribersForTesting(String.class).size(), 0);

        registry.register(new StringSubscriber());
        assertEquals(registry.getSubscribersForTesting(String.class).size(), 1);

        registry.register(new StringSubscriber());
        assertEquals(registry.getSubscribersForTesting(String.class).size(), 2);

        registry.register(new ObjectSubscriber());
        assertEquals(registry.getSubscribersForTesting(String.class).size(), 2);
        assertEquals(registry.getSubscribersForTesting(Object.class).size(), 1);
    }

    @Test
    public void testUnregister() {
        final StringSubscriber s1 = new StringSubscriber();
        final StringSubscriber s2 = new StringSubscriber();

        registry.register(s1);
        registry.register(s2);

        registry.unregister(s1);
        assertEquals(registry.getSubscribersForTesting(String.class).size(), 1);

        registry.unregister(s2);
        assertTrue(registry.getSubscribersForTesting(String.class).isEmpty());
    }

    @Test
    public void testUnregisterNotRegistered() {
        try {
            registry.unregister(new StringSubscriber());
            fail();
        } catch (final IllegalArgumentException expected) {
        }

        final StringSubscriber s1 = new StringSubscriber();
        registry.register(s1);
        try {
            registry.unregister(new StringSubscriber());
            fail();
        } catch (final IllegalArgumentException expected) {
            // a StringSubscriber was registered, but not the same one we tried to unregister
        }

        registry.unregister(s1);

        try {
            registry.unregister(s1);
            fail();
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testGetSubscribers() {
        assertEquals(Iterators.size(registry.getSubscribers(new Object())), 0);
        assertEquals(Iterators.size(registry.getSubscribers("")), 0);
        assertEquals(Iterators.size(registry.getSubscribers(1)), 0);

        registry.register(new StringSubscriber());
        assertEquals(Iterators.size(registry.getSubscribers("")), 1);
        assertEquals(Iterators.size(registry.getSubscribers(new Object())), 0);
        assertEquals(Iterators.size(registry.getSubscribers(1)), 0);

        registry.register(new StringSubscriber());
        assertEquals(Iterators.size(registry.getSubscribers("")), 2);
        assertEquals(Iterators.size(registry.getSubscribers(new Object())), 0);
        assertEquals(Iterators.size(registry.getSubscribers(1)), 0);

        // Object registered. All subclasses will be increased.
        final ObjectSubscriber objSub = new ObjectSubscriber();
        registry.register(objSub);
        assertEquals(Iterators.size(registry.getSubscribers(new Object())), 1);
        assertEquals(Iterators.size(registry.getSubscribers("")), 3);
        assertEquals(Iterators.size(registry.getSubscribers(1)), 1);

        registry.register(new IntegerSubscriber());
        assertEquals(Iterators.size(registry.getSubscribers(new Object())), 1);
        assertEquals(Iterators.size(registry.getSubscribers("")), 3);
        assertEquals(Iterators.size(registry.getSubscribers(1)), 2);

        // Unregister Object
        registry.unregister(objSub);

        assertEquals(Iterators.size(registry.getSubscribers(new Object())), 0);
        assertEquals(Iterators.size(registry.getSubscribers("")), 2);
        assertEquals(Iterators.size(registry.getSubscribers(1)), 1);
    }

    @Test
    public void testGetSubscribersReturnsImmutableSnapshot() {
        final StringSubscriber str1 = new StringSubscriber();
        final StringSubscriber str2 = new StringSubscriber();
        final ObjectSubscriber obj1 = new ObjectSubscriber();

        Iterator<org.killbill.common.eventbus.Subscriber> empty = registry.getSubscribers("");
        assertFalse(empty.hasNext());

        empty = registry.getSubscribers("");

        registry.register(str1);
        assertFalse(empty.hasNext());

        Iterator<org.killbill.common.eventbus.Subscriber> one = registry.getSubscribers("");
        assertEquals(str1, one.next().target);
        assertFalse(one.hasNext());

        one = registry.getSubscribers("");

        registry.register(str2);
        registry.register(obj1);

        Iterator<org.killbill.common.eventbus.Subscriber> three = registry.getSubscribers("");
        assertEquals(str1, one.next().target);
        assertFalse(one.hasNext());

        assertEquals(str1, three.next().target);
        assertEquals(str2, three.next().target);
        assertEquals(obj1, three.next().target);
        assertFalse(three.hasNext());

        three = registry.getSubscribers("");

        registry.unregister(str2);

        assertEquals(str1, three.next().target);
        assertEquals(str2, three.next().target);
        assertEquals(obj1, three.next().target);
        assertFalse(three.hasNext());

        final Iterator<org.killbill.common.eventbus.Subscriber> two = registry.getSubscribers("");
        assertEquals(str1, two.next().target);
        assertEquals(obj1, two.next().target);
        assertFalse(two.hasNext());
    }

    public static class StringSubscriber {

        @org.killbill.common.eventbus.Subscribe
        public void handle(final String s) {}
    }

    public static class IntegerSubscriber {

        @org.killbill.common.eventbus.Subscribe
        public void handle(final Integer i) {}
    }

    public static class ObjectSubscriber {

        @Subscribe
        public void handle(final Object o) {}
    }

    @Test
    public void testFlattenHierarchy() {
        assertEquals(
                Set.of(Object.class,
                       HierarchyFixtureInterface.class,
                       HierarchyFixtureSubinterface.class,
                       HierarchyFixtureParent.class,
                       HierarchyFixture.class),
                org.killbill.common.eventbus.SubscriberRegistry.flattenHierarchy(HierarchyFixture.class));
    }

    private interface HierarchyFixtureInterface {
        // Exists only for hierarchy mapping; no members.
    }

    private interface HierarchyFixtureSubinterface extends HierarchyFixtureInterface {
        // Exists only for hierarchy mapping; no members.
    }

    private static class HierarchyFixtureParent implements HierarchyFixtureSubinterface {
        // Exists only for hierarchy mapping; no members.
    }

    private static class HierarchyFixture extends HierarchyFixtureParent {
        // Exists only for hierarchy mapping; no members.
    }
}
