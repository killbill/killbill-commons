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

package org.killbill.commons.metrics.impl;

import java.util.HashMap;
import java.util.Map;

import org.killbill.commons.metrics.api.Counter;
import org.killbill.commons.metrics.api.Gauge;
import org.killbill.commons.metrics.api.Histogram;
import org.killbill.commons.metrics.api.Meter;
import org.killbill.commons.metrics.api.Metric;
import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.api.Timer;

public class NoOpMetricRegistry implements MetricRegistry {

    private final Map<String, Counter> counters = new HashMap<>();
    private final Map<String, Gauge<?>> gauges = new HashMap<>();
    private final Map<String, Meter> meters = new HashMap<>();
    private final Map<String, Histogram> histograms = new HashMap<>();
    private final Map<String, Timer> timers = new HashMap<>();

    @Override
    public Counter counter(final String name) {
        final Counter counter = new NoOpCounter();
        counters.put(name, counter);
        return counter;
    }

    @Override
    public <T> Gauge<T> gauge(final String name) {
        final Gauge<Object> gauge = new NoOpGauge<Object>(null);
        //noinspection unchecked
        gauges.put(name, gauge);
        return (Gauge<T>) gauge;
    }

    @Override
    public Meter meter(final String name) {
        final Meter meter = new NoOpMeter();
        meters.put(name, meter);
        return meter;
    }

    @Override
    public Histogram histogram(final String name) {
        final Histogram histogram = new NoOpHistogram();
        histograms.put(name, histogram);
        return histogram;
    }

    @Override
    public Timer timer(final String name) {
        final Timer timer = new NoOpTimer();
        timers.put(name, timer);
        return timer;
    }

    @Override
    public <T extends Metric> T register(final String name, final T metric) {
        if (metric instanceof Counter) {
            counters.put(name, (Counter) metric);
            return metric;
        } else if (metric instanceof Gauge) {
            gauges.put(name, (Gauge) metric);
            return metric;
        } else if (metric instanceof Histogram) {
            histograms.put(name, (Histogram) metric);
            return metric;
        } else if (metric instanceof Timer) {
            timers.put(name, (Timer) metric);
            return metric;
        }

        return null;
    }

    @Override
    public boolean remove(final String name) {
        if (gauges.remove(name) != null) {
            return true;
        } else if (counters.remove(name) != null) {
            return true;
        } else if (histograms.remove(name) != null) {
            return true;
        } else if (meters.remove(name) != null) {
            return true;
        } else if (timers.remove(name) != null) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Map<String, ?> getMetrics() {
        final Map<String, Object> metrics = new HashMap<>();
        metrics.putAll(gauges);
        metrics.putAll(counters);
        metrics.putAll(histograms);
        metrics.putAll(meters);
        metrics.putAll(timers);
        return metrics;
    }

    @Override
    public Map<String, Gauge<?>> getGauges() {
        return gauges;
    }

    @Override
    public Map<String, Counter> getCounters() {
        return counters;
    }

    @Override
    public Map<String, Histogram> getHistograms() {
        return histograms;
    }

    @Override
    public Map<String, Meter> getMeters() {
        return meters;
    }

    @Override
    public Map<String, Timer> getTimers() {
        return timers;
    }
}
