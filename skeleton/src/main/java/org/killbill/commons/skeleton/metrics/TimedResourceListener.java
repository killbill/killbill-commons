/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.commons.skeleton.metrics;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import javax.inject.Provider;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;

import org.glassfish.jersey.spi.ExceptionMappers;
import org.killbill.commons.metrics.TimedResource;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

/**
 * A listener which adds method interceptors to timed resource methods.
 */
public class TimedResourceListener implements TypeListener {

    private final Provider<ExceptionMappers> exceptionMappers;

    private final Provider<MetricRegistry> metricRegistry;

    public TimedResourceListener(final Provider<ExceptionMappers> exceptionMappers,
                                 final Provider<MetricRegistry> metricRegistry) {
        this.exceptionMappers = exceptionMappers;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public <T> void hear(final TypeLiteral<T> literal,
                         final TypeEncounter<T> encounter) {
        final Path pathAnnotation = literal.getRawType().getAnnotation(Path.class);
        if (pathAnnotation != null) {
            final String resourcePath = pathAnnotation.value();

            for (final Method method : literal.getRawType().getMethods()) {
                final TimedResource annotation = method.getAnnotation(TimedResource.class);
                if (annotation != null) {
                    final HttpMethod httpMethod = resourceHttpMethod(method);
                    if (httpMethod != null) {
                        final String metricName;
                        if (!annotation.name().trim().isEmpty()) {
                            metricName = annotation.name();
                        } else {
                            metricName = method.getName();
                        }
                        final TimedResourceInterceptor timedResourceInterceptor = new TimedResourceInterceptor(exceptionMappers,
                                                                                                               metricRegistry,
                                                                                                               resourcePath,
                                                                                                               metricName,
                                                                                                               httpMethod.value());
                        encounter.bindInterceptor(Matchers.only(method), timedResourceInterceptor);
                    }
                }
            }
        }
    }

    private HttpMethod resourceHttpMethod(final Method method) {
        for (final Annotation annotation: method.getAnnotations()) {
            final HttpMethod httpMethod = annotation.annotationType().getAnnotation(HttpMethod.class);
            if (httpMethod != null) {
                return httpMethod;
            }
        }
        return null;
    }
}
