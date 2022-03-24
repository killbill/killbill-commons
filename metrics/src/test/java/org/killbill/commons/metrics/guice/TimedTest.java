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

package org.killbill.commons.metrics.guice;

import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.api.Timer;
import org.killbill.commons.metrics.impl.NoOpMetricRegistry;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import static org.killbill.commons.metrics.guice.DeclaringClassMetricNamer.TIMED_SUFFIX;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TimedTest {

    private InstrumentedWithTimed instance;
    private MetricRegistry registry;

    @BeforeMethod(groups = "fast")
    void setup() {
        this.registry = new NoOpMetricRegistry();
        final Injector injector = Guice.createInjector(MetricsInstrumentationModule.builder().withMetricRegistry(registry).build());
        this.instance = injector.getInstance(InstrumentedWithTimed.class);
    }

    @Test(groups = "fast")
    void aTimedAnnotatedMethod() throws Exception {

        instance.doAThing();

        final Timer metric = registry.getTimers().get(String.format("%s.%s",
                                                                    InstrumentedWithTimed.class.getName(),
                                                                    "things"));

        assertMetricSetup(metric);

        assertEquals(metric.getCount(), 1L);
        //assertTrue(metric.getSnapshot().getMax() > NANOSECONDS.convert(5, MILLISECONDS));
        //assertTrue(metric.getSnapshot().getMax() < NANOSECONDS.convert(15, MILLISECONDS));
    }

    @Test(groups = "fast")
    void aTimedAnnotatedMethodWithDefaultScope() {

        instance.doAThingWithDefaultScope();

        final Timer metric = registry.getTimers().get(String.format("%s.%s.%s",
                                                                    InstrumentedWithTimed.class.getName(),
                                                                    "doAThingWithDefaultScope",
                                                                    TIMED_SUFFIX));

        assertMetricSetup(metric);
    }

    @Test(groups = "fast")
    void aTimedAnnotatedMethodWithProtectedScope() {

        instance.doAThingWithProtectedScope();

        final Timer metric = registry.getTimers().get(String.format("%s.%s.%s",
                                                                    InstrumentedWithTimed.class.getName(),
                                                                    "doAThingWithProtectedScope",
                                                                    TIMED_SUFFIX));

        assertMetricSetup(metric);
    }

    @Test(groups = "fast")
    void aTimedAnnotatedMethodWithAbsoluteName() {

        instance.doAThingWithAbsoluteName();

        final Timer metric = registry.getTimers().get("absoluteName");

        assertMetricSetup(metric);
    }

    private void assertMetricSetup(final Timer metric) {
        assertNotNull(metric);
    }
}
