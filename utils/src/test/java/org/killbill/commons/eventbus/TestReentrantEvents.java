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

import java.util.ArrayList;
import java.util.List;

import org.killbill.commons.utils.concurrent.DirectExecutor;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Validate that {@link EventBus} behaves carefully when listeners publish their own events.
 *
 * @author Jesse Wilson
 */
public class TestReentrantEvents {

    static final String FIRST = "one";
    static final Double SECOND = 2.0d;

    private EventBus bus;

    @BeforeMethod(groups = "fast", alwaysRun = true)
    public void beforeMethod() {
        // to post reentrant-ly, we should use Dispatcher.perThreadDispatchQueue()
        bus = new EventBus("", DirectExecutor.INSTANCE, Dispatcher.perThreadDispatchQueue(), new DefaultCatchableSubscriberExceptionsHandler());
    }

    @Test(groups = "fast")
    public void testNoReentrantEvents() {
        final ReentrantEventsHater hater = new ReentrantEventsHater();
        bus.register(hater);

        bus.post(FIRST);

        Assert.assertEquals(hater.eventsReceived, List.of(FIRST, SECOND), "ReentrantEventHater expected 2 events");
    }

    public class ReentrantEventsHater {
        boolean ready = true;
        List<Object> eventsReceived = new ArrayList<>();

        @Subscribe
        public void listenForStrings(final String event) {
            eventsReceived.add(event);
            ready = false;
            try {
                bus.post(SECOND);
            } finally {
                ready = true;
            }
        }

        @Subscribe
        public void listenForDoubles(final Double event) {
            Assert.assertTrue(ready, "I received an event when I wasn't ready!");
            eventsReceived.add(event);
        }
    }

    @Test(groups = "fast")
    public void testEventOrderingIsPredictable() {
        final EventProcessor processor = new EventProcessor();
        bus.register(processor);

        final EventRecorder recorder = new EventRecorder();
        bus.register(recorder);

        bus.post(FIRST);

        Assert.assertEquals(recorder.eventsReceived, List.of(FIRST, SECOND), "EventRecorder expected events in order");
    }

    public class EventProcessor {
        @Subscribe
        public void listenForStrings(final String ignored) {
            bus.post(SECOND);
        }
    }

    public static class EventRecorder {
        List<Object> eventsReceived = new ArrayList<>();

        @Subscribe
        public void listenForEverything(final Object event) {
            eventsReceived.add(event);
        }
    }
}
