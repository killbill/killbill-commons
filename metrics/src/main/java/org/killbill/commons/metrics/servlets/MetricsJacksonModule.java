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

package org.killbill.commons.metrics.servlets;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.killbill.commons.metrics.api.Counter;
import org.killbill.commons.metrics.api.Gauge;
import org.killbill.commons.metrics.api.Histogram;
import org.killbill.commons.metrics.api.Meter;
import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.api.Snapshot;
import org.killbill.commons.metrics.api.Timer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class MetricsJacksonModule extends Module {

    protected final TimeUnit rateUnit;
    protected final TimeUnit durationUnit;
    protected final boolean showSamples;

    public MetricsJacksonModule(final TimeUnit rateUnit, final TimeUnit durationUnit, final boolean showSamples) {
        this.rateUnit = rateUnit;
        this.durationUnit = durationUnit;
        this.showSamples = showSamples;
    }

    private static String calculateRateUnit(final TimeUnit unit, final String name) {
        final String s = unit.toString().toLowerCase(Locale.US);
        return name + '/' + s.substring(0, s.length() - 1);
    }

    @Override
    public String getModuleName() {
        return "metrics";
    }

    @Override
    public Version version() {
        return new Version(1, 0, 0, "", "org.kill-bill.commons", "killbill-metrics");
    }

    @Override
    public void setupModule(final SetupContext context) {
        context.addSerializers(new SimpleSerializers(Arrays.asList(
                new GaugeSerializer(),
                new CounterSerializer(),
                new HistogramSerializer(showSamples),
                new MeterSerializer(rateUnit),
                new TimerSerializer(rateUnit, durationUnit, showSamples),
                new MetricRegistrySerializer())));
    }

    @SuppressWarnings("rawtypes")
    private static class GaugeSerializer extends StdSerializer<Gauge> {

        private static final long serialVersionUID = 1L;

        private GaugeSerializer() {
            super(Gauge.class);
        }

        @Override
        public void serialize(final Gauge gauge,
                              final JsonGenerator json,
                              final SerializerProvider provider) throws IOException {
            json.writeStartObject();
            final Object value;
            try {
                value = gauge.getValue();
                json.writeObjectField("value", value);
            } catch (final RuntimeException e) {
                json.writeObjectField("error", e.toString());
            }
            json.writeEndObject();
        }
    }

    private static class CounterSerializer extends StdSerializer<Counter> {

        private static final long serialVersionUID = 1L;

        private CounterSerializer() {
            super(Counter.class);
        }

        @Override
        public void serialize(final Counter counter,
                              final JsonGenerator json,
                              final SerializerProvider provider) throws IOException {
            json.writeStartObject();
            json.writeNumberField("count", counter.getCount());
            json.writeEndObject();
        }
    }

    private static class HistogramSerializer extends StdSerializer<Histogram> {

        private static final long serialVersionUID = 1L;

        private final boolean showSamples;

        private HistogramSerializer(final boolean showSamples) {
            super(Histogram.class);
            this.showSamples = showSamples;
        }

        @Override
        public void serialize(final Histogram histogram,
                              final JsonGenerator json,
                              final SerializerProvider provider) throws IOException {
            json.writeStartObject();
            final Snapshot snapshot = histogram.getSnapshot();
            json.writeNumberField("count", histogram.getCount());
            json.writeNumberField("max", snapshot.getMax());
            json.writeNumberField("mean", snapshot.getMean());
            json.writeNumberField("min", snapshot.getMin());
            json.writeNumberField("p50", snapshot.getMedian());
            json.writeNumberField("p75", snapshot.get75thPercentile());
            json.writeNumberField("p95", snapshot.get95thPercentile());
            json.writeNumberField("p98", snapshot.get98thPercentile());
            json.writeNumberField("p99", snapshot.get99thPercentile());
            json.writeNumberField("p999", snapshot.get999thPercentile());

            if (showSamples) {
                json.writeObjectField("values", snapshot.getValues());
            }

            json.writeNumberField("stddev", snapshot.getStdDev());
            json.writeEndObject();
        }
    }

    private static class MeterSerializer extends StdSerializer<Meter> {

        private static final long serialVersionUID = 1L;

        private final String rateUnit;
        private final double rateFactor;

        public MeterSerializer(final TimeUnit rateUnit) {
            super(Meter.class);
            this.rateFactor = rateUnit.toSeconds(1);
            this.rateUnit = calculateRateUnit(rateUnit, "events");
        }

        @Override
        public void serialize(final Meter meter,
                              final JsonGenerator json,
                              final SerializerProvider provider) throws IOException {
            json.writeStartObject();
            json.writeNumberField("count", meter.getCount());
            json.writeNumberField("m15_rate", meter.getFifteenMinuteRate() * rateFactor);
            json.writeNumberField("m1_rate", meter.getOneMinuteRate() * rateFactor);
            json.writeNumberField("m5_rate", meter.getFiveMinuteRate() * rateFactor);
            json.writeNumberField("mean_rate", meter.getMeanRate() * rateFactor);
            json.writeStringField("units", rateUnit);
            json.writeEndObject();
        }
    }

    private static class TimerSerializer extends StdSerializer<Timer> {

        private static final long serialVersionUID = 1L;

        private final String rateUnit;
        private final double rateFactor;
        private final String durationUnit;
        private final double durationFactor;
        private final boolean showSamples;

        private TimerSerializer(final TimeUnit rateUnit,
                                final TimeUnit durationUnit,
                                final boolean showSamples) {
            super(Timer.class);
            this.rateUnit = calculateRateUnit(rateUnit, "calls");
            this.rateFactor = rateUnit.toSeconds(1);
            this.durationUnit = durationUnit.toString().toLowerCase(Locale.US);
            this.durationFactor = 1.0 / durationUnit.toNanos(1);
            this.showSamples = showSamples;
        }

        @Override
        public void serialize(final Timer timer,
                              final JsonGenerator json,
                              final SerializerProvider provider) throws IOException {
            json.writeStartObject();
            final Snapshot snapshot = timer.getSnapshot();
            json.writeNumberField("count", timer.getCount());
            json.writeNumberField("max", snapshot.getMax() * durationFactor);
            json.writeNumberField("mean", snapshot.getMean() * durationFactor);
            json.writeNumberField("min", snapshot.getMin() * durationFactor);

            json.writeNumberField("p50", snapshot.getMedian() * durationFactor);
            json.writeNumberField("p75", snapshot.get75thPercentile() * durationFactor);
            json.writeNumberField("p95", snapshot.get95thPercentile() * durationFactor);
            json.writeNumberField("p98", snapshot.get98thPercentile() * durationFactor);
            json.writeNumberField("p99", snapshot.get99thPercentile() * durationFactor);
            json.writeNumberField("p999", snapshot.get999thPercentile() * durationFactor);

            if (showSamples) {
                final long[] values = snapshot.getValues();
                final double[] scaledValues = new double[values.length];
                for (int i = 0; i < values.length; i++) {
                    scaledValues[i] = values[i] * durationFactor;
                }
                json.writeObjectField("values", scaledValues);
            }

            json.writeNumberField("stddev", snapshot.getStdDev() * durationFactor);
            json.writeNumberField("m15_rate", timer.getFifteenMinuteRate() * rateFactor);
            json.writeNumberField("m1_rate", timer.getOneMinuteRate() * rateFactor);
            json.writeNumberField("m5_rate", timer.getFiveMinuteRate() * rateFactor);
            json.writeNumberField("mean_rate", timer.getMeanRate() * rateFactor);
            json.writeStringField("duration_units", durationUnit);
            json.writeStringField("rate_units", rateUnit);
            json.writeEndObject();
        }
    }

    private static class MetricRegistrySerializer extends StdSerializer<MetricRegistry> {

        private static final long serialVersionUID = 1L;

        private MetricRegistrySerializer() {
            super(MetricRegistry.class);
        }

        @Override
        public void serialize(final MetricRegistry registry,
                              final JsonGenerator json,
                              final SerializerProvider provider) throws IOException {
            json.writeStartObject();
            json.writeObjectField("gauges", registry.getGauges());
            json.writeObjectField("counters", registry.getCounters());
            json.writeObjectField("histograms", registry.getHistograms());
            json.writeObjectField("meters", registry.getMeters());
            json.writeObjectField("timers", registry.getTimers());
            json.writeEndObject();
        }
    }
}