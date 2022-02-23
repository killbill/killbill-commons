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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.Guice;
import com.google.inject.Injector;

import static com.codahale.metrics.MetricRegistry.name;
import static com.palominolabs.metrics.guice.DeclaringClassMetricNamer.GAUGE_SUFFIX;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@SuppressWarnings("unchecked")
abstract class GaugeTestBase {

    MetricRegistry registry;
    private InstrumentedWithGauge instance;

    @BeforeMethod(groups = "fast")
    void setup() {
        this.registry = new MetricRegistry();
        final Injector injector =
                Guice.createInjector(MetricsInstrumentationModule.builder()
                                                                 .withMetricRegistry(registry)
                                                                 .withMetricNamer(getMetricNamer())
                                                                 .build());
        this.instance = injector.getInstance(InstrumentedWithGauge.class);
    }

    abstract MetricNamer getMetricNamer();

    @Test(groups = "fast")
    void aGaugeAnnotatedMethod() {
        instance.doAThing();

        final Gauge<String> metric = registry.getGauges().get(name(InstrumentedWithGauge.class, "things"));

        assertNotNull(metric);

        assertEquals(metric.getValue(), "poop");
    }

    @Test(groups = "fast")
    void aGaugeAnnotatedMethodWithDefaultName() {
        instance.doAnotherThing();

        final Gauge<String> metric = registry.getGauges().get(name(InstrumentedWithGauge.class,
                                                                   "doAnotherThing", GAUGE_SUFFIX));

        assertNotNull(metric);

        assertEquals(metric.getValue(), "anotherThing");
    }

    @Test(groups = "fast")
    void aGaugeAnnotatedMethodWithAbsoluteName() {
        instance.doAThingWithAbsoluteName();

        final Gauge<String> metric = registry.getGauges().get(name("absoluteName"));

        assertNotNull(metric);

        assertEquals(metric.getValue(), "anotherThingWithAbsoluteName");
    }

    @Test(groups = "fast")
    void aGaugeInSuperclass() {
        final Gauge<?> metric = registry.getGauges().get(name("gaugeParent"));

        assertNotNull(metric);
        assertEquals(metric.getValue(), "gaugeParent");
    }

    @Test(groups = "fast")
    void aPrivateGaugeInSuperclass() {
        final Gauge<?> metric = registry.getGauges().get(name("gaugeParentPrivate"));

        assertNotNull(metric);
        assertEquals(metric.getValue(), "gaugeParentPrivate");
    }

    @Test(groups = "fast")
    void aPrivateGauge() {
        final Gauge<?> metric = registry.getGauges().get(name(InstrumentedWithGauge.class, "gaugePrivate"));

        assertNotNull(metric);
        assertEquals(metric.getValue(), "gaugePrivate");
    }
}
