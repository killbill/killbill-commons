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

package com.palominolabs.metrics.guice;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.inject.Guice;
import com.google.inject.Injector;

import static com.codahale.metrics.MetricRegistry.name;
import static com.codahale.metrics.annotation.ExceptionMetered.DEFAULT_NAME_SUFFIX;
import static com.palominolabs.metrics.guice.DeclaringClassMetricNamer.METERED_SUFFIX;
import static com.palominolabs.metrics.guice.DeclaringClassMetricNamer.TIMED_SUFFIX;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class ExceptionMeteredTest {

    private InstrumentedWithExceptionMetered instance;
    private MetricRegistry registry;

    @BeforeMethod(groups = "fast")
    public void setup() {
        this.registry = new MetricRegistry();
        final Injector injector = Guice.createInjector(MetricsInstrumentationModule.builder().withMetricRegistry(registry).build());
        this.instance = injector.getInstance(InstrumentedWithExceptionMetered.class);
    }

    @Test(groups = "fast")
    void anExceptionMeteredAnnotatedMethodWithPublicScope() {

        final Meter metric = registry.getMeters().get(name(InstrumentedWithExceptionMetered.class, "exceptionCounter"));
        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 0L);

        try {
            instance.explodeWithPublicScope(true);
            fail("Expected an exception to be thrown");
        } catch (final RuntimeException e) {
            // Swallow the expected exception
        }

        assertEquals(metric.getCount(), 1L);
    }

    @Test(groups = "fast")
    void anExceptionMeteredAnnotatedMethod_WithNoMetricName() {

        final Meter metric = registry.getMeters().get(name(InstrumentedWithExceptionMetered.class,
                                                           "explodeForUnnamedMetric", DEFAULT_NAME_SUFFIX));
        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 0L);

        try {
            instance.explodeForUnnamedMetric();
            fail("Expected an exception to be thrown");
        } catch (final RuntimeException e) {
            // Swallow the expected exception
        }

        assertEquals(metric.getCount(), 1L);
    }

    @Test(groups = "fast")
    void anExceptionMeteredAnnotatedMethod_WithName() {

        final Meter metric = registry.getMeters().get(name(InstrumentedWithExceptionMetered.class, "n"));
        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 0L);

        try {
            instance.explodeForMetricWithName();
            fail("Expected an exception to be thrown");
        } catch (final RuntimeException e) {
            // Swallow the expected exception
        }

        assertEquals(metric.getCount(), 1L);
    }

    @Test(groups = "fast")
    void anExceptionMeteredAnnotatedMethod_WithAbsoluteName() {

        final Meter metric = registry.getMeters().get(name("absoluteName"));
        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 0L);

        try {
            instance.explodeForMetricWithAbsoluteName();
            fail("Expected an exception to be thrown");
        } catch (final RuntimeException e) {
            // Swallow the expected exception
        }

        assertEquals(metric.getCount(), 1L);
    }

    @Test(groups = "fast")
    void anExceptionMeteredAnnotatedMethod_WithPublicScopeButNoExceptionThrown() {

        final Meter metric = registry.getMeters().get(name(InstrumentedWithExceptionMetered.class,
                                                           "exceptionCounter"));
        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 0L);

        instance.explodeWithPublicScope(false);

        assertEquals(metric.getCount(), 0L);
    }

    @Test(groups = "fast")
    void anExceptionMeteredAnnotatedMethod_WithDefaultScope() {

        final Meter metric = registry.getMeters().get(name(InstrumentedWithExceptionMetered.class,
                                                           "explodeWithDefaultScope", DEFAULT_NAME_SUFFIX));
        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 0L);

        try {
            instance.explodeWithDefaultScope();
            fail("Expected an exception to be thrown");
        } catch (final RuntimeException ignored) {
        }

        assertEquals(metric.getCount(), 1L);
    }

    @Test(groups = "fast")
    void anExceptionMeteredAnnotatedMethod_WithProtectedScope() {

        final Meter metric = registry.getMeters().get(name(InstrumentedWithExceptionMetered.class,
                                                           "explodeWithProtectedScope", DEFAULT_NAME_SUFFIX));

        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 0L);

        try {
            instance.explodeWithProtectedScope();
            fail("Expected an exception to be thrown");
        } catch (final RuntimeException ignored) {
        }

        assertEquals(metric.getCount(), 1L);
    }

    @Test(groups = "fast")
    void anExceptionMeteredAnnotatedMethod_WithPublicScope_AndSpecificTypeOfException() {

        final Meter metric = registry.getMeters().get(name(InstrumentedWithExceptionMetered.class,
                                                           "failures"));
        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 0L);
        try {
            instance.errorProneMethod(new MyException());
            fail("Expected an exception to be thrown");
        } catch (final MyException ignored) {
        }

        assertEquals(metric.getCount(), 1L);
    }

    @Test(groups = "fast")
    void anExceptionMeteredAnnotatedMethod_WithPublicScope_AndSubclassesOfSpecifiedException() {

        final Meter metric = registry.getMeters().get(name(InstrumentedWithExceptionMetered.class,
                                                           "failures"));
        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 0L);
        try {
            instance.errorProneMethod(new MySpecialisedException());
            fail("Expected an exception to be thrown");
        } catch (final MyException ignored) {
        }

        assertEquals(metric.getCount(), 1L);
    }

    @Test(groups = "fast")
    void anExceptionMeteredAnnotatedMethod_WithPublicScope_ButDifferentTypeOfException() {

        final Meter metric = registry.getMeters().get(name(InstrumentedWithExceptionMetered.class,
                                                           "failures"));
        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 0L);
        try {
            instance.errorProneMethod(new MyOtherException());
            fail("Expected an exception to be thrown");
        } catch (final MyOtherException ignored) {
        }

        assertEquals(metric.getCount(), 0L);
    }

    @Test(groups = "fast")
    void anExceptionMeteredAnnotatedMethod_WithExtraOptions() {

        try {
            instance.causeAnOutOfBoundsException();
        } catch (final ArrayIndexOutOfBoundsException ignored) {

        }

        final Meter metric = registry.getMeters().get(name(InstrumentedWithExceptionMetered.class,
                                                           "things"));

        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 1L);
    }

    @Test(groups = "fast")
    void aMethodAnnotatedWithBothATimerAndAnExceptionCounter() {

        final Timer timedMetric = registry.getTimers().get(name(InstrumentedWithExceptionMetered.class,
                                                                "timedAndException", TIMED_SUFFIX));

        final Meter errorMetric = registry.getMeters().get(name(InstrumentedWithExceptionMetered.class,
                                                                "timedAndException", DEFAULT_NAME_SUFFIX));

        assertNotNull(timedMetric);

        assertNotNull(errorMetric);

        // Counts should start at zero
        assertEquals(timedMetric.getCount(), 0L);

        assertEquals(errorMetric.getCount(), 0L);

        // Invoke, but don't throw an exception
        instance.timedAndException(null);

        assertEquals(timedMetric.getCount(), 1L);

        assertEquals(errorMetric.getCount(), 0L);

        // Invoke and throw an exception
        try {
            instance.timedAndException(new RuntimeException());
            fail("Should have thrown an exception");
        } catch (final Exception ignored) {
        }

        assertEquals(timedMetric.getCount(), 2L);

        assertEquals(errorMetric.getCount(), 1L);
    }

    @Test(groups = "fast")
    void aMethodAnnotatedWithBothAMeteredAndAnExceptionCounter() {

        final Meter meteredMetric = registry.getMeters().get(name(InstrumentedWithExceptionMetered.class,
                                                                  "meteredAndException", METERED_SUFFIX));

        final Meter errorMetric = registry.getMeters().get(name(InstrumentedWithExceptionMetered.class,
                                                                "meteredAndException", DEFAULT_NAME_SUFFIX));

        assertNotNull(meteredMetric);
        assertNotNull(errorMetric);

        // Counts should start at zero
        assertEquals(meteredMetric.getCount(), 0L);

        assertEquals(errorMetric.getCount(), 0L);

        // Invoke, but don't throw an exception
        instance.meteredAndException(null);

        assertEquals(meteredMetric.getCount(), 1L);

        assertEquals(errorMetric.getCount(), 0L);

        // Invoke and throw an exception
        try {
            instance.meteredAndException(new RuntimeException());
            fail("Should have thrown an exception");
        } catch (final Exception ignored) {
        }

        assertEquals(meteredMetric.getCount(),
                     2L);

        assertEquals(errorMetric.getCount(), 1L);
    }

    private void assertMetricIsSetup(final Meter metric) {
        assertNotNull(metric);
    }

    @SuppressWarnings("serial")
    private static class MyOtherException extends RuntimeException {
    }

    @SuppressWarnings("serial")
    private static class MySpecialisedException extends MyException {
    }
}
