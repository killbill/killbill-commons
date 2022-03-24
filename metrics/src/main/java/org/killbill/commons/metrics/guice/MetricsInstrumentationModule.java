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

import javax.annotation.Nonnull;

import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.api.annotation.Counted;
import org.killbill.commons.metrics.api.annotation.ExceptionMetered;
import org.killbill.commons.metrics.api.annotation.Gauge;
import org.killbill.commons.metrics.api.annotation.Metered;
import org.killbill.commons.metrics.api.annotation.Timed;

import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import org.killbill.commons.metrics.guice.annotation.AnnotationResolver;
import org.killbill.commons.metrics.guice.annotation.MethodAnnotationResolver;

/**
 * A Guice module which instruments methods annotated with the {@link Metered}, {@link Timed}, {@link Gauge}, {@link
 * Counted}, and {@link ExceptionMetered} annotations.
 *
 * @see Gauge
 * @see Metered
 * @see Timed
 * @see ExceptionMetered
 * @see Counted
 * @see MeteredInterceptor
 * @see TimedInterceptor
 * @see GaugeInjectionListener
 */
public class MetricsInstrumentationModule extends AbstractModule {

    private final MetricRegistry metricRegistry;
    private final Matcher<? super TypeLiteral<?>> matcher;
    private final MetricNamer metricNamer;
    private final AnnotationResolver annotationResolver;

    /**
     * @param metricRegistry     The registry to use when creating meters, etc. for annotated methods.
     * @param matcher            The matcher to determine which types to look for metrics in
     * @param metricNamer        The metric namer to use when creating names for metrics for annotated methods
     * @param annotationResolver The annotation provider
     */
    private MetricsInstrumentationModule(final MetricRegistry metricRegistry,
                                         final Matcher<? super TypeLiteral<?>> matcher,
                                         final MetricNamer metricNamer,
                                         final AnnotationResolver annotationResolver) {
        this.metricRegistry = metricRegistry;
        this.matcher = matcher;
        this.metricNamer = metricNamer;
        this.annotationResolver = annotationResolver;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void configure() {
        bindListener(matcher, new MeteredListener(metricRegistry, metricNamer, annotationResolver));
        bindListener(matcher, new TimedListener(metricRegistry, metricNamer, annotationResolver));
        bindListener(matcher, new GaugeListener(metricRegistry, metricNamer, annotationResolver));
        bindListener(matcher, new ExceptionMeteredListener(metricRegistry, metricNamer, annotationResolver));
        bindListener(matcher, new CountedListener(metricRegistry, metricNamer, annotationResolver));
    }

    public static class Builder {

        private MetricRegistry metricRegistry;
        private Matcher<? super TypeLiteral<?>> matcher = Matchers.any();
        private MetricNamer metricNamer = new GaugeInstanceClassMetricNamer();
        private AnnotationResolver annotationResolver = new MethodAnnotationResolver();

        /**
         * @param metricRegistry The registry to use when creating meters, etc. for annotated methods.
         * @return this
         */
        @Nonnull
        public Builder withMetricRegistry(@Nonnull final MetricRegistry metricRegistry) {
            this.metricRegistry = metricRegistry;

            return this;
        }

        /**
         * @param matcher The matcher to determine which types to look for metrics in
         * @return this
         */
        @Nonnull
        public Builder withMatcher(@Nonnull final Matcher<? super TypeLiteral<?>> matcher) {
            this.matcher = matcher;

            return this;
        }

        /**
         * @param metricNamer The metric namer to use when creating names for metrics for annotated methods
         * @return this
         */
        @Nonnull
        public Builder withMetricNamer(@Nonnull final MetricNamer metricNamer) {
            this.metricNamer = metricNamer;

            return this;
        }

        /**
         * @param annotationResolver Annotation resolver to use
         * @return this
         */
        @Nonnull
        public Builder withAnnotationMatcher(@Nonnull final AnnotationResolver annotationResolver) {
            this.annotationResolver = annotationResolver;

            return this;
        }

        @Nonnull
        public MetricsInstrumentationModule build() {
            return new MetricsInstrumentationModule(
                    Preconditions.checkNotNull(metricRegistry),
                    Preconditions.checkNotNull(matcher),
                    Preconditions.checkNotNull(metricNamer),
                    Preconditions.checkNotNull(annotationResolver)
            );
        }
    }
}
