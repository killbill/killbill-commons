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

import org.killbill.commons.metrics.api.Meter;
import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.impl.NoOpMetricRegistry;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import static org.killbill.commons.metrics.guice.DeclaringClassMetricNamer.METERED_SUFFIX;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class MeteredTest {

    private InstrumentedWithMetered instance;
    private MetricRegistry registry;

    @BeforeMethod(groups = "fast")
    void setup() {
        this.registry = new NoOpMetricRegistry();
        final Injector injector =
                Guice.createInjector(MetricsInstrumentationModule.builder().withMetricRegistry(registry).build());
        this.instance = injector.getInstance(InstrumentedWithMetered.class);
    }

    @Test(groups = "fast")
    void aMeteredAnnotatedMethod() {

        instance.doAThing();

        final Meter metric = registry.getMeters().get(String.format("%s.%s",
                                                                    InstrumentedWithMetered.class.getName(),
                                                                    "things"));

        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 1L);
    }

    @Test(groups = "fast")
    void aMeteredAnnotatedMethodWithDefaultScope() {

        final Meter metric = registry.getMeters().get(String.format("%s.%s.%s",
                                                                    InstrumentedWithMetered.class.getName(),
                                                                    "doAThingWithDefaultScope",
                                                                    METERED_SUFFIX));
        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 0L);

        instance.doAThingWithDefaultScope();

        assertEquals(metric.getCount(), 1L);
    }

    @Test(groups = "fast")
    void aMeteredAnnotatedMethodWithProtectedScope() {

        final Meter metric = registry.getMeters().get(String.format("%s.%s.%s",
                                                                    InstrumentedWithMetered.class.getName(),
                                                                    "doAThingWithProtectedScope",
                                                                    METERED_SUFFIX));

        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 0L);

        instance.doAThingWithProtectedScope();

        assertEquals(metric.getCount(), 1L);
    }

    @Test(groups = "fast")
    void aMeteredAnnotatedMethodWithName() {

        final Meter metric = registry.getMeters().get(String.format("%s.%s", InstrumentedWithMetered.class.getName(), "n"));

        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 0L);

        instance.doAThingWithName();

        assertEquals(metric.getCount(), 1L);
    }

    @Test(groups = "fast")
    void aMeteredAnnotatedMethodWithAbsoluteName() {
        final Meter metric = registry.getMeters().get("nameAbs");

        assertMetricIsSetup(metric);

        assertEquals(metric.getCount(), 0L);

        instance.doAThingWithAbsoluteName();

        assertEquals(metric.getCount(), 1L);
    }

    private void assertMetricIsSetup(final Meter metric) {
        assertNotNull(metric);
    }
}
