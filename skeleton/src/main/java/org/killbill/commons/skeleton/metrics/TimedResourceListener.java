/*
 * Copyright 2010-2014 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package org.killbill.commons.skeleton.metrics;

import java.lang.reflect.Method;

import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

/**
 * A listener which adds method interceptors to timed resource methods.
 */
public class TimedResourceListener implements TypeListener {

    private final SettableFuture<MetricRegistry> metricsRegistryFuture = SettableFuture.create();

    @Inject
    public void setMetricsRegistry(final MetricRegistry metricsRegistry) {
        metricsRegistryFuture.set(metricsRegistry);
    }

    @Override
    public <T> void hear(final TypeLiteral<T> literal,
                         final TypeEncounter<T> encounter) {
        for (Method method : literal.getRawType().getMethods()) {
            final TimedResource annotation = method.getAnnotation(TimedResource.class);
            if (annotation != null) {
                final String name = annotation.name().isEmpty() ? method.getName() : annotation.name();
                final TimedResourceMetric timer = new TimedResourceMetric(metricsRegistryFuture,
                                                                          literal.getRawType(),
                                                                          name,
                                                                          annotation.defaultStatusCode());
                encounter.bindInterceptor(Matchers.only(method), new TimedResourceInterceptor(timer));
            }
        }
    }
}
