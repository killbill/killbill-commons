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
 * Generates for the metrics corresponding to the various metric annotations.
 */
public interface MetricNamer {

    @Nonnull
    String getNameForCounted(@Nonnull Method method, @Nonnull Counted counted);

    @Nonnull
    String getNameForExceptionMetered(@Nonnull Method method, @Nonnull ExceptionMetered exceptionMetered);

    /**
     * For AOP-wrapped method invocations (which is how all metrics other than Gauges have to be handled), there isn't a
     * way to handle annotated methods defined in superclasses since we can't AOP superclass methods. Gauges, however,
     * are invoked without requiring AOP, so gauges from superclasses are available.
     *
     * @param instanceClass the type being instantiated
     * @param method        the annotated method (which may belong to a supertype)
     * @param gauge         the annotation
     * @return a name
     */
    @Nonnull
    String getNameForGauge(@Nonnull Class<?> instanceClass, @Nonnull Method method, @Nonnull Gauge gauge);

    @Nonnull
    String getNameForMetered(@Nonnull Method method, @Nonnull Metered metered);

    @Nonnull
    String getNameForTimed(@Nonnull Method method, @Nonnull Timed timed);
}
