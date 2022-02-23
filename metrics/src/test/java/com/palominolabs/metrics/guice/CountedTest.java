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

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.Guice;
import com.google.inject.Injector;

import static com.codahale.metrics.MetricRegistry.name;
import static com.palominolabs.metrics.guice.DeclaringClassMetricNamer.COUNTER_SUFFIX;
import static com.palominolabs.metrics.guice.DeclaringClassMetricNamer.COUNTER_SUFFIX_MONOTONIC;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertEquals;

public class CountedTest {

    private InstrumentedWithCounter instance;
    private MetricRegistry registry;

    @BeforeMethod(groups = "fast")
    void setup() {
        this.registry = new MetricRegistry();
        final Injector injector = Guice.createInjector(MetricsInstrumentationModule.builder().withMetricRegistry(registry).build());
        this.instance = injector.getInstance(InstrumentedWithCounter.class);
    }

    @Test(groups = "fast")
    void aCounterAnnotatedMethod() {
        instance.doAThing();

        final Counter metric = registry.getCounters().get(name(InstrumentedWithCounter.class, "things"));

        assertNotNull(metric);

        assertEquals(metric.getCount(), (long) 1);
    }

    @Test(groups = "fast")
    void aCounterAnnotatedMethodWithDefaultName() {
        instance.doAnotherThing();

        final Counter metric = registry.getCounters().get(name(InstrumentedWithCounter.class,
                                                               "doAnotherThing", COUNTER_SUFFIX_MONOTONIC));

        assertNotNull(metric);

        assertEquals(metric.getCount(), (long) 1);
    }

    @Test(groups = "fast")
    void aCounterAnnotatedMethodWithDefaultNameAndMonotonicFalse() {
        instance.doYetAnotherThing();

        final Counter metric = registry.getCounters().get(name(InstrumentedWithCounter.class,
                                                               "doYetAnotherThing", COUNTER_SUFFIX));

        assertNotNull(metric);

        // if things are working well then this should still be zero...
        assertEquals(metric.getCount(), (long) 0);
    }

    @Test(groups = "fast")
    void aCounterAnnotatedMethodWithAbsoluteName() {
        instance.doAThingWithAbsoluteName();

        final Counter metric = registry.getCounters().get(name("absoluteName"));

        assertNotNull(metric);

        assertEquals(metric.getCount(), (long) 1);
    }

    /**
     * Test to document the current (correct but regrettable) behavior.
     * <p>
     * Crawling the injected class's supertype hierarchy doesn't really accomplish anything because AOPing supertype
     * methods doesn't work.
     * <p>
     * In certain cases (e.g. a public type that inherits a public method from a non-public supertype), a synthetic
     * bridge method is generated in the subtype that invokes the supertype method, and this does copy the annotations
     * of the supertype method. However, we can't allow intercepting synthetic bridge methods in general: when a subtype
     * overrides a generic supertype's method with a more specifically typed method that would not override the
     * type-erased supertype method, a bridge method matching the supertype's erased signature is generated, but with
     * the subtype's method's annotation. It's not OK to intercept that synthetic method because that would lead to
     * double-counting, etc, since we also would intercept the regular non-synthetic method.
     * <p>
     * Thus, we cannot intercept synthetic methods to maintain correctness, so we also lose out on one small way that we
     * could intercept annotated methods in superclasses.
     */
    @Test(groups = "fast")
    void aCounterForSuperclassMethod() {
        instance.counterParent();

        final Counter metric = registry.getCounters().get(name("counterParent"));

        // won't be created because we don't bother looking for supertype methods
        assertNull(metric);
    }
}
