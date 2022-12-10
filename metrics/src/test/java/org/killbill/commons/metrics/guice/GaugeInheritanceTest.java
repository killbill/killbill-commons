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
import org.killbill.commons.metrics.api.annotation.Gauge;
import org.killbill.commons.metrics.guice.DeclaringClassMetricNamer;
import org.killbill.commons.metrics.guice.MetricsInstrumentationModule;
import org.killbill.commons.metrics.impl.NoOpMetricRegistry;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import static org.testng.Assert.assertEquals;

public class GaugeInheritanceTest {

    @Test(groups = "fast")
    void testInheritance() {
        final MetricRegistry registry = new NoOpMetricRegistry();
        final Injector injector = Guice
                .createInjector(MetricsInstrumentationModule.builder().withMetricRegistry(registry).build());
        injector.getInstance(Parent.class);
        injector.getInstance(Child1.class);
        injector.getInstance(Child2.class);

        // gauge in parent class is registered separately for each

        assertEquals(registry.getGauges().get(String.format("%s.%s.%s", Parent.class.getName(), "aGauge", DeclaringClassMetricNamer.GAUGE_SUFFIX))
                             .getValue(),
                     0);
        assertEquals(registry.getGauges().get(String.format("%s.%s.%s", Child1.class.getName(), "aGauge", DeclaringClassMetricNamer.GAUGE_SUFFIX))
                             .getValue(),
                     1);
        assertEquals(registry.getGauges().get(String.format("%s.%s.%s", Child2.class.getName(), "aGauge", DeclaringClassMetricNamer.GAUGE_SUFFIX))
                             .getValue(),
                     2);
    }

    static class Parent {

        @Gauge
        int aGauge() {
            return complexInternalCalculation();
        }

        int complexInternalCalculation() {
            return 0;
        }
    }

    static class Child1 extends Parent {

        @Override
        int complexInternalCalculation() {
            return 1;
        }
    }

    static class Child2 extends Parent {

        @Override
        int complexInternalCalculation() {
            return 2;
        }
    }
}
