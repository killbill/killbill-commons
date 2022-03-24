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

import org.killbill.commons.metrics.api.Counter;
import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.impl.NoOpMetricRegistry;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class CountInvocationGenericSubtypeTest {

    private GenericThing<String> instance;
    private MetricRegistry registry;

    @BeforeMethod(groups = "fast")
    void setup() {
        this.registry = new NoOpMetricRegistry();
        final Injector injector = Guice.createInjector(MetricsInstrumentationModule.builder().withMetricRegistry(registry).build());
        this.instance = injector.getInstance(StringThing.class);
    }

    @Test(groups = "fast")
    void testCountsInvocationOfGenericOverride() {
        instance.doThing("foo");

        final Counter metric = registry.getCounters().get("stringThing");

        assertNotNull(metric);

        assertEquals(metric.getCount(), 1);
    }
}
