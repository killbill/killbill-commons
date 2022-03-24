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

import javax.annotation.Nullable;

import org.aopalliance.intercept.MethodInterceptor;
import org.killbill.commons.metrics.api.Counter;
import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.api.annotation.Counted;

import org.killbill.commons.metrics.guice.annotation.AnnotationResolver;

/**
 * A listener which adds method interceptors to counted methods.
 */
public class CountedListener extends DeclaredMethodsTypeListener {

    private final MetricRegistry metricRegistry;
    private final MetricNamer metricNamer;
    private final AnnotationResolver annotationResolver;

    public CountedListener(final MetricRegistry metricRegistry, final MetricNamer metricNamer,
                           final AnnotationResolver annotationResolver) {
        this.metricRegistry = metricRegistry;
        this.metricNamer = metricNamer;
        this.annotationResolver = annotationResolver;
    }

    @Nullable
    @Override
    protected MethodInterceptor getInterceptor(final Method method) {
        final Counted annotation = annotationResolver.findAnnotation(Counted.class, method);
        if (annotation != null) {
            final Counter counter = metricRegistry.counter(metricNamer.getNameForCounted(method, annotation));
            return new CountedInterceptor(counter, annotation);
        }
        return null;
    }
}
