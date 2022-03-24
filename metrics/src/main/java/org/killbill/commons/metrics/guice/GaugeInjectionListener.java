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

import org.killbill.commons.metrics.api.Gauge;
import org.killbill.commons.metrics.api.MetricRegistry;

import com.google.inject.spi.InjectionListener;

/**
 * An injection listener which creates a gauge for the declaring class with the given name (or the method's name, if
 * none was provided) which returns the value returned by the annotated method.
 */
public class GaugeInjectionListener<I> implements InjectionListener<I> {

    private final MetricRegistry metricRegistry;
    private final String metricName;
    private final Method method;

    public GaugeInjectionListener(final MetricRegistry metricRegistry, final String metricName, final Method method) {
        this.metricRegistry = metricRegistry;
        this.metricName = metricName;
        this.method = method;
    }

    @Override
    public void afterInjection(final I i) {
        metricRegistry.gauge(metricName,
                             new Gauge<>() {
                                 @Override
                                 public Object getValue() {
                                     try {
                                         return method.invoke(i);
                                     } catch (final Exception e) {
                                         return new RuntimeException(e);
                                     }
                                 }
                             });
    }
}
