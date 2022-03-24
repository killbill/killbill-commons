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

import org.killbill.commons.metrics.api.annotation.Gauge;

/**
 * For gauges (which can reside in superclasses of the type being instantiated), this will use the instantiated type for
 * the resulting metric name no matter what superclass declares the gauge method. This allows for a gauge located in a
 * superclass to be used by multiple inheritors without causing a duplicate metric name clash.
 * <p>
 * For other metric types, which are not available on superclass methods, the declaring class (which would be the same
 * as the instantiated class) is used, as in {@link DeclaringClassMetricNamer}.
 */
public class GaugeInstanceClassMetricNamer extends DeclaringClassMetricNamer {

    @Nonnull
    @Override
    public String getNameForGauge(@Nonnull final Class<?> instanceClass, @Nonnull final Method method, @Nonnull final Gauge gauge) {
        if (gauge.absolute()) {
            return gauge.name();
        }

        if (gauge.name().isEmpty()) {
            return String.format("%s.%s.%s", instanceClass.getName(), method.getName(), GAUGE_SUFFIX);
        }

        return String.format("%s.%s", instanceClass.getName(), gauge.name());
    }
}
