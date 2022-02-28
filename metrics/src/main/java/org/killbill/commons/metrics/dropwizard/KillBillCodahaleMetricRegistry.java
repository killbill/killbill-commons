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

package org.killbill.commons.metrics.dropwizard;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.killbill.commons.metrics.api.Counter;
import org.killbill.commons.metrics.api.Gauge;
import org.killbill.commons.metrics.api.Histogram;
import org.killbill.commons.metrics.api.Meter;
import org.killbill.commons.metrics.api.Metric;
import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.api.Timer;

public class KillBillCodahaleMetricRegistry implements MetricRegistry {

    private final com.codahale.metrics.MetricRegistry dwMetricRegistry;

    public KillBillCodahaleMetricRegistry() {
        this(new com.codahale.metrics.MetricRegistry());
    }

    public KillBillCodahaleMetricRegistry(final com.codahale.metrics.MetricRegistry dwMetricRegistry) {
        this.dwMetricRegistry = dwMetricRegistry;
    }

    @Override
    public Counter counter(final String name) {
        return new KillBillCodahaleCounter(dwMetricRegistry.counter(name));
    }

    @Override
    public <T> Gauge<T> gauge(final String name) {
        return new KillBillCodahaleGauge<T>(dwMetricRegistry.gauge(name));
    }

    @Override
    public Histogram histogram(final String name) {
        return new KillBillCodahaleHistogram(dwMetricRegistry.histogram(name));
    }

    @Override
    public Meter meter(final String name) {
        return new KillBillCodahaleMeter(dwMetricRegistry.meter(name));
    }

    @Override
    public Timer timer(final String name) {
        return new KillBillCodahaleTimer(dwMetricRegistry.timer(name));
    }

    @Override
    public <T extends Metric> T register(final String name, final T metric) {
        if (metric instanceof Counter) {
            dwMetricRegistry.register(name, new CodahaleCounter((Counter) metric));
            return metric;
        } else if (metric instanceof Gauge) {
            dwMetricRegistry.register(name, new CodahaleGauge((Gauge) metric));
            return metric;
        } else if (metric instanceof Histogram) {
            dwMetricRegistry.register(name, new CodahaleHistogram((Histogram) metric));
            return metric;
        } else if (metric instanceof Timer) {
            dwMetricRegistry.register(name, new CodahaleTimer((Timer) metric));
            return metric;
        }

        return null;
    }

    @Override
    public boolean remove(final String name) {
        return false;
    }

    @Override
    public Map<String, ?> getMetrics() {
        return dwMetricRegistry.getMetrics();
    }

    @Override
    public Map<String, Gauge<?>> getGauges() {
        final Map<String, Gauge<?>> gauges = new HashMap<>();
        for (final Entry<String, com.codahale.metrics.Gauge> entry : dwMetricRegistry.getGauges().entrySet()) {
            gauges.put(entry.getKey(), new KillBillCodahaleGauge(entry.getValue()));
        }
        return gauges;
    }

    @Override
    public Map<String, Meter> getMeters() {
        final Map<String, Meter> meters = new HashMap<>();
        for (final Entry<String, com.codahale.metrics.Meter> entry : dwMetricRegistry.getMeters().entrySet()) {
            meters.put(entry.getKey(), new KillBillCodahaleMeter(entry.getValue()));
        }
        return meters;
    }

    @Override
    public Map<String, Counter> getCounters() {
        final Map<String, Counter> counters = new HashMap<>();
        for (final Entry<String, com.codahale.metrics.Counter> entry : dwMetricRegistry.getCounters().entrySet()) {
            counters.put(entry.getKey(), new KillBillCodahaleCounter(entry.getValue()));
        }
        return counters;
    }

    @Override
    public Map<String, Histogram> getHistograms() {
        final Map<String, Histogram> histograms = new HashMap<>();
        for (final Entry<String, com.codahale.metrics.Histogram> entry : dwMetricRegistry.getHistograms().entrySet()) {
            histograms.put(entry.getKey(), new KillBillCodahaleHistogram(entry.getValue()));
        }
        return histograms;
    }

    @Override
    public Map<String, Timer> getTimers() {
        final Map<String, Timer> timers = new HashMap<>();
        for (final Entry<String, com.codahale.metrics.Timer> entry : dwMetricRegistry.getTimers().entrySet()) {
            timers.put(entry.getKey(), new KillBillCodahaleTimer(entry.getValue()));
        }
        return timers;
    }
}
