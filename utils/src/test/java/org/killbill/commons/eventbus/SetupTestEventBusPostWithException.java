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

package org.killbill.commons.eventbus;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.testng.Assert;

class SetupTestEventBusPostWithException {

    static final Random rand = new Random();

    EventBus eventBus;
    Subscriber subscriberA;
    Subscriber subscriberB;

    void busSetup() {
        eventBus = new EventBus("testing");

        subscriberA = new SubscriberA();
        eventBus.register(subscriberA);

        subscriberB = new SubscriberB();
        eventBus.register(subscriberB);
    }

    void checkEventsSeen(final Subscriber subscriber, final MyEvent... events) {
        final long threadId = Thread.currentThread().getId();
        Collection<MyEvent> myEvents = subscriber.events.get(threadId);
        if (myEvents != null && !myEvents.isEmpty()) {
            subscriber.events.put(threadId, Collections.emptySet());
        } else {
            myEvents = Collections.emptyList();
        }

        Assert.assertEquals(myEvents.size(), events.length);
        int i = 0;
        for (final MyEvent myEvent : myEvents) {
            Assert.assertSame(myEvent, events[i]);
            i++;
        }
    }

    abstract static class Subscriber {

        /**
         * Originally, {@code events} instantiation is as follows:
         * final Multimap<Long, MyEvent> events = Multimaps.synchronizedMultimap(HashMultimap.<Long, MyEvent>create());
         *
         * We can change this using {@link org.killbill.commons.utils.collect.MultiValueMap}, and add new method
         * {@code #removeAll()} that needed in {@code #checkEventsSeen()} above.
         *
         * The problem is, guava's HashMultimap doesn't allow duplicate values, so the behaviour quite different from
         * our {@link org.killbill.commons.utils.collect.MultiValueHashMap} and causing test fails. Thus, we just use
         * simple map here.
         */
        final Map<Long, Set<MyEvent>> events = Collections.synchronizedMap(new HashMap<>());

        static String exceptionMarker(final String id) {
            return String.format("%s-%s", Thread.currentThread().getId(), id);
        }

        void maybeThrow(final MyEvent event, final String id) {
            if (event.exceptionThrowerIds.contains(id)) {
                throw new RuntimeException(exceptionMarker(id));
            }
        }
    }

    protected static final class SubscriberA extends Subscriber {

        @Subscribe
        public void onEvent(final MyEvent event) {
            maybeThrow(event, "A");
            events.put(Thread.currentThread().getId(), Set.of(event));
        }
    }

    protected static final class SubscriberB extends Subscriber {

        @Subscribe
        public void onEvent(final MyEvent event) {
            maybeThrow(event, "B");
            events.put(Thread.currentThread().getId(), Set.of(event));
        }
    }

    protected static final class MyEvent {

        private final UUID id;
        private final Set<String> exceptionThrowerIds;

        MyEvent(final UUID id, final String... exceptionThrowerIds) {
            this.id = id;
            this.exceptionThrowerIds = Set.of(exceptionThrowerIds);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final MyEvent myEvent = (MyEvent) o;

            return Objects.equals(id, myEvent.id);
        }

        @Override
        public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }
    }
}
