/*
 * Copyright 2017 Groupon, Inc
 * Copyright 2017 The Billing Project, LLC
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

package com.google.common.eventbus;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestEventBusThatThrowsException extends TestSetupEventBusThatThrowsException {

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        busSetup();
    }

    @AfterMethod(groups = "fast")
    public void tearDown() throws Exception {
        Assert.assertNull(eventBus.exceptionHandler.lastException.get());
    }

    @Test(groups = "fast")
    public void testThrowFirstExceptionFromSubscribersV1() throws Exception {
        final MyEvent event = new MyEvent(UUID.randomUUID(), "A");

        try {
            eventBus.postWithException(event);
            Assert.fail();
        } catch (final EventBusException e) {
            Assert.assertTrue(e.getCause() instanceof InvocationTargetException);
            Assert.assertTrue(e.getCause().getCause() instanceof RuntimeException);
            Assert.assertEquals(e.getCause().getCause().getMessage(), Subscriber.exceptionMarker("A"));
        }

        checkEventsSeen(subscriberA);
        checkEventsSeen(subscriberB, event);
    }

    @Test(groups = "fast")
    public void testThrowFirstExceptionFromSubscribersV2() throws Exception {
        final MyEvent event = new MyEvent(UUID.randomUUID(), "B");

        try {
            eventBus.postWithException(event);
            Assert.fail();
        } catch (final EventBusException e) {
            Assert.assertTrue(e.getCause() instanceof InvocationTargetException);
            Assert.assertTrue(e.getCause().getCause() instanceof RuntimeException);
            Assert.assertEquals(e.getCause().getCause().getMessage(), Subscriber.exceptionMarker("B"));
        }

        checkEventsSeen(subscriberA, event);
        checkEventsSeen(subscriberB);
    }

    @Test(groups = "fast")
    public void testThrowFirstExceptionFromSubscribersV3() throws Exception {
        final MyEvent event = new MyEvent(UUID.randomUUID(), "A", "B");

        try {
            eventBus.postWithException(event);
            Assert.fail();
        } catch (final EventBusException e) {
            Assert.assertTrue(e.getCause() instanceof InvocationTargetException);
            Assert.assertTrue(e.getCause().getCause() instanceof RuntimeException);
            // The second exception won't be seen
            Assert.assertEquals(e.getCause().getCause().getMessage(), Subscriber.exceptionMarker("A"));
        }

        checkEventsSeen(subscriberA);
        checkEventsSeen(subscriberB);
    }
}
