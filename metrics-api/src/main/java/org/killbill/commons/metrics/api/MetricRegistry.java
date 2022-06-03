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

package org.killbill.commons.metrics.api;

import java.util.function.DoubleSupplier;

public interface MetricRegistry {

    /**
     * Return the {@link Counter} registered under this name; or create and register
     * a new {@link Counter} if none is registered.
     *
     * @param name the name of the metric
     * @return a new or pre-existing {@link Counter}
     */
    Counter counter(String name, String ...labelNames);

    /**
     * Return the {@link Gauge} registered under this name; or create and register
     * a new {@link Gauge} if none is registered.
     *
     * @param name the name of the metric
     * @param supplier the underlying Gauge
     * @return a new or pre-existing {@link Gauge}
     */
    Gauge gauge(String name, String ...labelNames);

    Gauge gauge(String name, DoubleSupplier supplier, String ...labelNames);

    /**
     * Return the {@link Histogram} registered under this name; or create and register
     * a new {@link Histogram} if none is registered using the implementation's default
     * bucket distribution.
     *
     * @param name the name of the metric
     * @return a new or pre-existing {@link Histogram}
     */
    Histogram histogram(String name, String ...labelNames);

    Histogram histogram(String name, double[] buckets, String ...labelNames);

    Summary summary(String name, String ...labelNames);

    Summary summary(String name, double[] quantiles, String ...labelNames);

    /**
     * Removes the metric with the given name.
     *
     * @param name the name of the metric
     * @return whether or not the metric was removed
     */
    boolean remove(String name, String ...labelNames);
}
