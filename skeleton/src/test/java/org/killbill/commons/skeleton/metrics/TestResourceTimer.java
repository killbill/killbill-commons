/*
 * Copyright 2010-2014 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package org.killbill.commons.skeleton.metrics;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableMap;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = "fast")
public class TestResourceTimer {

    private static final String RESOURCE_METRICS_PREFIX = "kb_resource";

    private MetricRegistry metricRegistry;

    @BeforeMethod
    public void setup() {
        metricRegistry = new MetricRegistry();
    }

    @Test
    public void testMetricName() {
        final String resourcePath = "/1.0/kb/payments";
        final String resourceName = "getPayment";
        final String httpMethod = "GET";

        final ResourceTimer resourceTimer = new ResourceTimer(resourcePath, resourceName, httpMethod, null, metricRegistry);
        resourceTimer.update(200, 1, TimeUnit.MILLISECONDS);

        final String expectedMetricName = expectedMetricName(resourcePath, resourceName, httpMethod, null, "2xx", 200);
        final Timer timer = (Timer) metricRegistry.getMetrics().get(expectedMetricName);
        Assert.assertNotNull(timer, "Failed to create metric with expected name");
        Assert.assertEquals(1, timer.getCount());
    }

    private String expectedMetricName(String resourcePath, String resourceName, String httpMethod, String tagsValues, String statusGroup, int status) {
        if (tagsValues == null || tagsValues.trim().isEmpty()) {
            return String.format("%s.%s.%s.%s.%s.%s", RESOURCE_METRICS_PREFIX, resourcePath, resourceName, httpMethod, statusGroup, status);
        }
        return String.format("%s.%s.%s.%s.%s.%s.%s", RESOURCE_METRICS_PREFIX, resourcePath, resourceName, httpMethod, tagsValues, statusGroup, status);
    }

    @Test
    public void testMetricNameWithTags() {
        final String resourcePath = "/1.0/kb/payments";
        final String resourceName = "create";
        final String httpMethod = "POST";
        final Map<String, Object> tags = (Map) ImmutableMap.builder().put("transactionType", "AUTHORIZE").build();

        final ResourceTimer resourceTimer = new ResourceTimer(resourcePath, resourceName, httpMethod, tags, metricRegistry);
        resourceTimer.update(501, 1, TimeUnit.MILLISECONDS);

        final String expectedMetricName = expectedMetricName(resourcePath, resourceName, httpMethod, "AUTHORIZE", "5xx", 501);
        final Timer timer = (Timer) metricRegistry.getMetrics().get(expectedMetricName);
        Assert.assertNotNull(timer, "Failed to create metric with expected name: " + expectedMetricName);
        Assert.assertEquals(1, timer.getCount());
    }
}
