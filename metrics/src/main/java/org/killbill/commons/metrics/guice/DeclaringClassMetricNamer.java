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

import java.lang.reflect.Method;

import javax.annotation.Nonnull;

import org.killbill.commons.metrics.api.annotation.Counted;
import org.killbill.commons.metrics.api.annotation.ExceptionMetered;
import org.killbill.commons.metrics.api.annotation.Gauge;
import org.killbill.commons.metrics.api.annotation.Metered;
import org.killbill.commons.metrics.api.annotation.Timed;

/**
 * Uses the name fields in the metric annotations, if present, or the method declaring class and method name.
 */
public class DeclaringClassMetricNamer implements MetricNamer {

    static final String COUNTER_SUFFIX = "counter";
    static final String COUNTER_SUFFIX_MONOTONIC = "current";
    static final String GAUGE_SUFFIX = "gauge";
    static final String METERED_SUFFIX = "meter";
    static final String TIMED_SUFFIX = "timer";

    @Nonnull
    @Override
    public String getNameForCounted(@Nonnull final Method method, @Nonnull final Counted counted) {
        if (counted.absolute()) {
            return counted.name();
        }

        if (counted.name().isEmpty()) {
            if (counted.monotonic()) {
                return String.format("%s.%s.%s", method.getDeclaringClass().getName(), method.getName(), COUNTER_SUFFIX_MONOTONIC);
            } else {
                return String.format("%s.%s.%s", method.getDeclaringClass().getName(), method.getName(), COUNTER_SUFFIX);
            }
        }

        return String.format("%s.%s", method.getDeclaringClass().getName(), counted.name());
    }

    @Nonnull
    @Override
    public String getNameForExceptionMetered(@Nonnull final Method method, @Nonnull final ExceptionMetered exceptionMetered) {
        if (exceptionMetered.absolute()) {
            return exceptionMetered.name();
        }

        if (exceptionMetered.name().isEmpty()) {
            return String.format("%s.%s.%s", method.getDeclaringClass().getName(), method.getName(), ExceptionMetered.DEFAULT_NAME_SUFFIX);
        }

        return String.format("%s.%s", method.getDeclaringClass().getName(), exceptionMetered.name());
    }

    @Nonnull
    @Override
    public String getNameForGauge(@Nonnull final Class<?> instanceClass, @Nonnull final Method method, @Nonnull final Gauge gauge) {
        if (gauge.absolute()) {
            return gauge.name();
        }

        if (gauge.name().isEmpty()) {
            return String.format("%s.%s.%s", method.getDeclaringClass().getName(), method.getName(), GAUGE_SUFFIX);
        }

        return String.format("%s.%s", method.getDeclaringClass().getName(), gauge.name());
    }

    @Nonnull
    @Override
    public String getNameForMetered(@Nonnull final Method method, @Nonnull final Metered metered) {
        if (metered.absolute()) {
            return metered.name();
        }

        if (metered.name().isEmpty()) {
            return String.format("%s.%s.%s", method.getDeclaringClass().getName(), method.getName(), METERED_SUFFIX);
        }

        return String.format("%s.%s", method.getDeclaringClass().getName(), metered.name());
    }

    @Nonnull
    @Override
    public String getNameForTimed(@Nonnull final Method method, @Nonnull final Timed timed) {
        if (timed.absolute()) {
            return timed.name();
        }

        if (timed.name().isEmpty()) {
            return String.format("%s.%s.%s", method.getDeclaringClass().getName(), method.getName(), TIMED_SUFFIX);
        }

        return String.format("%s.%s", method.getDeclaringClass().getName(), timed.name());
    }
}
