/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.eventbus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test case for {@link AsyncEventBus}.
 *
 * @author Cliff Biffle
 */
public class TestAsyncEventBus {
    private static final String EVENT = "Hello";

    /** The executor we use to fake asynchronicity. */
    private FakeExecutor executor;

    private AsyncEventBus bus;

    @BeforeMethod
    protected void setUp() {
        executor = new FakeExecutor();
        bus = new AsyncEventBus(executor);
    }

    @Test
    public void testBasicDistribution() {
        final StringCatcher catcher = new StringCatcher();
        bus.register(catcher);

        // We post the event, but our Executor will not deliver it until instructed.
        bus.post(EVENT);

        final List<String> events = catcher.getEvents();
        Assert.assertTrue(events.isEmpty(), "No events should be delivered synchronously.");

        // Now we find the task in our Executor and explicitly activate it.
        final List<Runnable> tasks = executor.getTasks();
        Assert.assertEquals(tasks.size(), 1, "One event dispatch task should be queued.");

        tasks.get(0).run();

        Assert.assertEquals(events.size(), 1, "One event should be delivered.");
        Assert.assertEquals(events.get(0), EVENT, "Correct string should be delivered.");
    }

    /**
     * An {@link Executor} wanna-be that simply records the tasks it's given. Arguably the Worst
     * Executor Ever.
     *
     * @author cbiffle
     */
    public static class FakeExecutor implements Executor {
        List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(final Runnable task) {
            tasks.add(task);
        }

        public List<Runnable> getTasks() {
            return tasks;
        }
    }
}
