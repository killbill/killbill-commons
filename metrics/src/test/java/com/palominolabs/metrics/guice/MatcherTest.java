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
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matcher;

import static com.codahale.metrics.MetricRegistry.name;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class MatcherTest {

    private InstrumentedWithTimed timedInstance;
    private InstrumentedWithMetered meteredInstance;
    private MetricRegistry registry;

    @BeforeMethod(groups = "fast")
    void setup() {
        this.registry = new MetricRegistry();
        final Matcher<? super TypeLiteral<?>> matcher = new AbstractMatcher<TypeLiteral<?>>() {
            @Override
            public boolean matches(final TypeLiteral<?> typeLiteral) {
                return InstrumentedWithMetered.class.isAssignableFrom(typeLiteral.getRawType());
            }
        };
        final Injector injector = Guice.createInjector(MetricsInstrumentationModule.builder().withMetricRegistry(registry).withMatcher(matcher).build());
        this.timedInstance = injector.getInstance(InstrumentedWithTimed.class);
        this.meteredInstance = injector.getInstance(InstrumentedWithMetered.class);
    }

    @Test(groups = "fast")
    void aTimedAnnotatedMethod() throws Exception {

        timedInstance.doAThing();

        final Timer metric = registry.getTimers().get(name(InstrumentedWithTimed.class,
                                                           "things"));

        assertNull(metric);
    }

    @Test(groups = "fast")
    void aMeteredAnnotatedMethod() {

        meteredInstance.doAThing();

        final Meter metric = registry.getMeters().get(name(InstrumentedWithMetered.class, "things"));

        assertNotNull(metric);
        assertEquals(metric.getCount(), 1L);
    }
}
