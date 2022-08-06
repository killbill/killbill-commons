/*
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

package org.killbill.commons.skeleton.modules;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import javax.inject.Singleton;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;

import org.aopalliance.intercept.ConstructorInterceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.glassfish.hk2.api.Descriptor;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.InterceptionService;
import org.glassfish.jersey.spi.ExceptionMappers;
import org.killbill.commons.metrics.api.annotation.TimedResource;
import org.killbill.commons.skeleton.metrics.TimedResourceInterceptor;

import org.killbill.commons.metrics.api.MetricRegistry;

@Singleton
public class TimedInterceptionService implements InterceptionService {

    private final Set<String> resourcePackages;
    private final ExceptionMappers exceptionMappers;
    private final MetricRegistry metricRegistry;

    public TimedInterceptionService(final Set<String> resourcePackage,
                                    final ExceptionMappers exceptionMappers,
                                    final MetricRegistry metricRegistry) {
        this.resourcePackages = resourcePackage;
        this.exceptionMappers = exceptionMappers;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public Filter getDescriptorFilter() {
        return new Filter() {
            @Override
            public boolean matches(final Descriptor d) {
                final String clazz = d.getImplementation();
                for (final String resourcePackage : resourcePackages) {
                    if (clazz.startsWith(resourcePackage)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    @Override
    public List<MethodInterceptor> getMethodInterceptors(final Method method) {
        final TimedResource annotation = method.getAnnotation(TimedResource.class);
        if (annotation == null) {
            return null;
        }

        final Path pathAnnotation = method.getDeclaringClass().getAnnotation(Path.class);
        if (pathAnnotation == null) {
            return null;
        }
        final String resourcePath = pathAnnotation.value();

        final HttpMethod httpMethod = resourceHttpMethod(method);
        if (httpMethod == null) {
            return null;
        }

        final String metricName;
        if (!annotation.name().trim().isEmpty()) {
            metricName = annotation.name();
        } else {
            metricName = method.getName();
        }
        final MethodInterceptor timedResourceInterceptor = new TimedResourceInterceptor(exceptionMappers,
                                                                                        metricRegistry,
                                                                                        resourcePath,
                                                                                        metricName,
                                                                                        httpMethod.value());
        return List.of(timedResourceInterceptor);
    }

    private HttpMethod resourceHttpMethod(final Method method) {
        for (final Annotation annotation : method.getAnnotations()) {
            final HttpMethod httpMethod = annotation.annotationType().getAnnotation(HttpMethod.class);
            if (httpMethod != null) {
                return httpMethod;
            }
        }
        return null;
    }

    @Override
    public List<ConstructorInterceptor> getConstructorInterceptors(final Constructor<?> constructor) {
        return null;
    }
}
