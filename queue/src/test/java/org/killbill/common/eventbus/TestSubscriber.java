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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.killbill.common.eventbus.AllowConcurrentEvents;
import org.killbill.common.eventbus.EventBus;
import org.killbill.common.eventbus.Subscribe;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests for {@link org.killbill.common.eventbus.Subscriber}.
 *
 * @author Cliff Biffle
 * @author Colin Decker
 */
public class TestSubscriber {

    private static final Object FIXTURE_ARGUMENT = new Object();

    private org.killbill.common.eventbus.EventBus bus;
    private boolean methodCalled;
    private Object methodArgument;

    @BeforeMethod
    protected void setUp() {
        bus = new EventBus();
        methodCalled = false;
        methodArgument = null;
    }

    @Test
    public void testCreate() {
        final org.killbill.common.eventbus.Subscriber s1 = org.killbill.common.eventbus.Subscriber.create(bus, this, getTestSubscriberMethod("recordingMethod"));
        Assert.assertTrue(s1 instanceof org.killbill.common.eventbus.Subscriber.SynchronizedSubscriber);

        // a thread-safe method should not create a synchronized subscriber
        final org.killbill.common.eventbus.Subscriber s2 = org.killbill.common.eventbus.Subscriber.create(bus, this, getTestSubscriberMethod("threadSafeMethod"));
        Assert.assertFalse(s2 instanceof org.killbill.common.eventbus.Subscriber.SynchronizedSubscriber);
        // assertThat(s2).isNotInstanceOf(Subscriber.SynchronizedSubscriber.class);
    }

    @Test
    public void testInvokeSubscriberMethod_basicMethodCall() throws Throwable {
        final Method method = getTestSubscriberMethod("recordingMethod");
        final org.killbill.common.eventbus.Subscriber subscriber = org.killbill.common.eventbus.Subscriber.create(bus, this, method);

        subscriber.invokeSubscriberMethod(FIXTURE_ARGUMENT);

        Assert.assertTrue(methodCalled, "Subscriber must call provided method");
        Assert.assertSame(methodArgument, FIXTURE_ARGUMENT, "Subscriber argument must be exactly the provided object.");
    }

    @Test
    public void testInvokeSubscriberMethod_exceptionWrapping() {
        final Method method = getTestSubscriberMethod("exceptionThrowingMethod");
        final org.killbill.common.eventbus.Subscriber subscriber = org.killbill.common.eventbus.Subscriber.create(bus, this, method);

        try {
            subscriber.invokeSubscriberMethod(FIXTURE_ARGUMENT);
            Assert.fail("Subscribers whose methods throw must throw InvocationTargetException");
        } catch (final InvocationTargetException ignored) {
        }
    }

    @Test
    public void testInvokeSubscriberMethod_errorPassthrough() throws Throwable {
        final Method method = getTestSubscriberMethod("errorThrowingMethod");
        final org.killbill.common.eventbus.Subscriber subscriber = org.killbill.common.eventbus.Subscriber.create(bus, this, method);

        try {
            subscriber.invokeSubscriberMethod(FIXTURE_ARGUMENT);
            Assert.fail("Subscribers whose methods throw Errors must rethrow them");
        } catch (final JudgmentError ignored) {
        }
    }

    private Method getTestSubscriberMethod(final String name) {
        try {
            return getClass().getDeclaredMethod(name, Object.class);
        } catch (final NoSuchMethodException e) {
            throw new AssertionError();
        }
    }

    /**
     * Records the provided object in {@link #methodArgument} and sets {@link #methodCalled}. This
     * method is called reflectively by Subscriber during tests, and must remain public.
     *
     * @param arg argument to record.
     */
    @org.killbill.common.eventbus.Subscribe
    public void recordingMethod(final Object arg) {
        Assert.assertFalse(methodCalled);
        methodCalled = true;
        methodArgument = arg;
    }

    @org.killbill.common.eventbus.Subscribe
    public void exceptionThrowingMethod(final Object ignored) throws Exception {
        throw new IntentionalException();
    }

    /** Local exception subclass to check variety of exception thrown. */
    static class IntentionalException extends Exception {

        private static final long serialVersionUID = -2500191180248181379L;
    }

    @org.killbill.common.eventbus.Subscribe
    public void errorThrowingMethod(final Object ignored) {
        throw new JudgmentError();
    }

    @Subscribe
    @AllowConcurrentEvents
    public void threadSafeMethod(final Object ignored) {}

    /** Local Error subclass to check variety of error thrown. */
    static class JudgmentError extends Error {

        private static final long serialVersionUID = 634248373797713373L;
    }
}
