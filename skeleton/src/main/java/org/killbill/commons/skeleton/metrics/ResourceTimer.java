/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.commons.skeleton.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.api.Timer;
import org.killbill.commons.utils.Joiner;

public class ResourceTimer {

    private static final Joiner METRIC_NAME_JOINER = Joiner.on('.');

    private final String resourcePath;
    private final String name;
    private final String httpMethod;

    private final Map<String, Object> tags;
    private final MetricRegistry registry;

    public ResourceTimer(final String resourcePath, final String name, final String httpMethod, final Map<String, Object> tags, final MetricRegistry registry) {
        this.resourcePath = resourcePath;
        this.name = name;
        this.httpMethod = httpMethod;
        this.tags = tags;
        this.registry = registry;
    }

    public void update(final int responseStatus, final long duration, final TimeUnit unit) {
        final String metricName;
        if (tags != null && !tags.isEmpty()) {
            final String tags = METRIC_NAME_JOINER.join(getTagsValues());
            metricName = METRIC_NAME_JOINER.join(
                    escapeMetrics("kb_resource", resourcePath, name, httpMethod, tags, responseStatusGroup(responseStatus), responseStatus));
        } else {
            metricName = METRIC_NAME_JOINER.join(
                    escapeMetrics("kb_resource", resourcePath, name, httpMethod, responseStatusGroup(responseStatus), responseStatus));
        }
        // Letting metric registry deal with unique metric creation
        final Timer timer = registry.timer(metricName);
        timer.update(duration, unit);
    }

    private List<String> escapeMetrics(final Object... names) {
        final List<String> result = new ArrayList<String>(names.length);
        for (final Object name : names) {
            final String metricName = String.valueOf(name);
            result.add(metricName.replaceAll("\\.", "_"));
        }
        return result;
    }

    private List<Object> getTagsValues() {
        final List<Object> values = new ArrayList<Object>(tags.values().size());
        for (final Object value : tags.values()) {
            if (value != null) {
                values.add(value);
            } else {
                values.add("null");
            }
        }
        return values;
    }

    private String responseStatusGroup(final int responseStatus) {
        return String.format("%sxx", responseStatus / 100);
    }
}
