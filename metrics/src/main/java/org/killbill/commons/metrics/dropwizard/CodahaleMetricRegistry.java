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

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;

public class CodahaleMetricRegistry extends MetricRegistry {

    private final org.killbill.commons.metrics.api.MetricRegistry killbillMetricRegistry;

    public CodahaleMetricRegistry(final org.killbill.commons.metrics.api.MetricRegistry killbillMetricRegistry) {
        this.killbillMetricRegistry = killbillMetricRegistry;
    }

    @Override
    public <T extends Metric> T register(final String name, final T metric) throws IllegalArgumentException {
        throw new UnsupportedOperationException("Register via the Kill Bill MetricRegistry instead");
    }

    @Override
    public void registerAll(final MetricSet metrics) throws IllegalArgumentException {
        throw new UnsupportedOperationException("Register via the Kill Bill MetricRegistry instead");
    }

    @Override
    public Counter counter(final String name) {
        throw new UnsupportedOperationException("Register via the Kill Bill MetricRegistry instead");
    }

    @Override
    public Counter counter(final String name, final MetricSupplier<Counter> supplier) {
        throw new UnsupportedOperationException("Register via the Kill Bill MetricRegistry instead");
    }

    @Override
    public Histogram histogram(final String name) {
        throw new UnsupportedOperationException("Register via the Kill Bill MetricRegistry instead");
    }

    @Override
    public Histogram histogram(final String name, final MetricSupplier<Histogram> supplier) {
        throw new UnsupportedOperationException("Register via the Kill Bill MetricRegistry instead");
    }

    @Override
    public Meter meter(final String name) {
        throw new UnsupportedOperationException("Register via the Kill Bill MetricRegistry instead");
    }

    @Override
    public Meter meter(final String name, final MetricSupplier<Meter> supplier) {
        throw new UnsupportedOperationException("Register via the Kill Bill MetricRegistry instead");
    }

    @Override
    public Timer timer(final String name) {
        throw new UnsupportedOperationException("Register via the Kill Bill MetricRegistry instead");
    }

    @Override
    public Timer timer(final String name, final MetricSupplier<Timer> supplier) {
        throw new UnsupportedOperationException("Register via the Kill Bill MetricRegistry instead");
    }

    @Override
    public <T extends Gauge> T gauge(final String name) {
        throw new UnsupportedOperationException("Register via the Kill Bill MetricRegistry instead");
    }

    @Override
    public <T extends Gauge> T gauge(final String name, final MetricSupplier<T> supplier) {
        throw new UnsupportedOperationException("Register via the Kill Bill MetricRegistry instead");
    }

    @Override
    public boolean remove(final String name) {
        throw new UnsupportedOperationException("Unregister via the Kill Bill MetricRegistry instead");
    }

    @Override
    public void removeMatching(final MetricFilter filter) {
        throw new UnsupportedOperationException("Unregister via the Kill Bill MetricRegistry instead");
    }

    @Override
    public SortedSet<String> getNames() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(getMetrics().keySet()));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public SortedMap<String, Gauge> getGauges() {
        return getGauges(MetricFilter.ALL);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public SortedMap<String, Gauge> getGauges(final MetricFilter filter) {
        return getMetrics(Gauge.class, filter);
    }

    @Override
    public SortedMap<String, Counter> getCounters() {
        return getCounters(MetricFilter.ALL);
    }

    @Override
    public SortedMap<String, Counter> getCounters(final MetricFilter filter) {
        return getMetrics(Counter.class, filter);
    }

    @Override
    public SortedMap<String, Histogram> getHistograms() {
        return getHistograms(MetricFilter.ALL);
    }

    @Override
    public SortedMap<String, Histogram> getHistograms(final MetricFilter filter) {
        return getMetrics(Histogram.class, filter);
    }

    @Override
    public SortedMap<String, Meter> getMeters() {
        return getMeters(MetricFilter.ALL);
    }

    @Override
    public SortedMap<String, Meter> getMeters(final MetricFilter filter) {
        return getMetrics(Meter.class, filter);
    }

    @Override
    public SortedMap<String, Timer> getTimers() {
        return getTimers(MetricFilter.ALL);
    }

    @Override
    public SortedMap<String, Timer> getTimers(final MetricFilter filter) {
        return getMetrics(Timer.class, filter);
    }

    @SuppressWarnings("unchecked")
    private <T extends Metric> SortedMap<String, T> getMetrics(final Class<T> klass, final MetricFilter filter) {
        final TreeMap<String, T> timers = new TreeMap<>();
        for (final Map.Entry<String, Metric> entry : getMetrics().entrySet()) {
            if (klass.isInstance(entry.getValue()) && filter.matches(entry.getKey(),
                                                                     entry.getValue())) {
                timers.put(entry.getKey(), (T) entry.getValue());
            }
        }
        return Collections.unmodifiableSortedMap(timers);
    }

    @Override
    public void registerAll(final String prefix, final MetricSet metrics) throws IllegalArgumentException {
        throw new UnsupportedOperationException("Register via the Kill Bill MetricRegistry instead");
    }

    @Override
    public Map<String, Metric> getMetrics() {
        // Note that we don't cache anything as the Codahale MetricRegistry needs to reflect the Kill Bill one in real time
        final ConcurrentMap<String, Metric> dropwizardMap = new ConcurrentHashMap<>();
        for (final Entry<String, org.killbill.commons.metrics.api.Counter> entry : killbillMetricRegistry.getCounters().entrySet()) {
            dropwizardMap.put(entry.getKey(), new CodahaleCounter(entry.getValue()));
        }
        for (final Entry<String, org.killbill.commons.metrics.api.Histogram> entry : killbillMetricRegistry.getHistograms().entrySet()) {
            dropwizardMap.put(entry.getKey(), new CodahaleHistogram(entry.getValue()));
        }
        for (final Entry<String, org.killbill.commons.metrics.api.Gauge<?>> entry : killbillMetricRegistry.getGauges().entrySet()) {
            dropwizardMap.put(entry.getKey(), new CodahaleGauge(entry.getValue()));
        }
        for (final Entry<String, org.killbill.commons.metrics.api.Meter> entry : killbillMetricRegistry.getMeters().entrySet()) {
            dropwizardMap.put(entry.getKey(), new CodahaleMeter(entry.getValue()));
        }
        for (final Entry<String, org.killbill.commons.metrics.api.Timer> entry : killbillMetricRegistry.getTimers().entrySet()) {
            dropwizardMap.put(entry.getKey(), new CodahaleTimer(entry.getValue()));
        }
        return dropwizardMap;
    }
}
