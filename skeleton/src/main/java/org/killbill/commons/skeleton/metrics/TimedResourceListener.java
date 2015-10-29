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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import javax.inject.Provider;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;

import org.killbill.commons.metrics.TimedResource;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

/**
 * A listener which adds method interceptors to timed resource methods.
 */
public class TimedResourceListener implements TypeListener {

    private final Provider<GuiceContainer> guiceContainer;

    private final Provider<MetricRegistry> metricRegistry;

    public TimedResourceListener(Provider<GuiceContainer> guiceContainer,
                                     Provider<MetricRegistry> metricRegistry) {
        this.guiceContainer = guiceContainer;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public <T> void hear(final TypeLiteral<T> literal,
                         final TypeEncounter<T> encounter) {
        final Path pathAnnotation = literal.getRawType().getAnnotation(Path.class);
        if (pathAnnotation != null) {
            final String resourcePath = pathAnnotation.value();

            for (Method method : literal.getRawType().getMethods()) {
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
                        final TimedResourceInterceptor timedResourceInterceptor = new TimedResourceInterceptor(
                                guiceContainer, metricRegistry, resourcePath, metricName, httpMethod.value());
                        encounter.bindInterceptor(Matchers.only(method), timedResourceInterceptor);
                    }
                }
            }
        }
    }

    private HttpMethod resourceHttpMethod(Method method) {
        for (Annotation annotation: method.getAnnotations()) {
            HttpMethod httpMethod = annotation.annotationType().getAnnotation(HttpMethod.class);
            if (httpMethod != null) {
                return httpMethod;
            }
        }
        return null;
    }
}
