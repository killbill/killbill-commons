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

import org.testng.annotations.Test;

import com.codahale.metrics.Gauge;

import static com.codahale.metrics.MetricRegistry.name;
import static com.palominolabs.metrics.guice.DeclaringClassMetricNamer.GAUGE_SUFFIX;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

class GaugeInstanceClassNamerTest extends GaugeTestBase {

    @Override
    MetricNamer getMetricNamer() {
        return new GaugeInstanceClassMetricNamer();
    }

    @Test
    void aGaugeWithoutNameInSuperclass() {
        // named for the instantiated class
        final Gauge<?> metric =
                registry.getGauges().get(name(InstrumentedWithGauge.class, "justAGaugeFromParent",
                                              GAUGE_SUFFIX));

        assertNotNull(metric);
        assertEquals(metric.getValue(), "justAGaugeFromParent");
    }
}
