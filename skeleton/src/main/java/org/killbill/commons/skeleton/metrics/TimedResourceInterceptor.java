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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.glassfish.jersey.spi.ExceptionMappers;
import org.killbill.commons.metrics.MetricTag;

import com.codahale.metrics.MetricRegistry;

/**
 * A method interceptor which times the execution of the annotated resource method.
 */
public class TimedResourceInterceptor implements MethodInterceptor {

    private final Map<String, Map<String, Object>> metricTagsByMethod = new ConcurrentHashMap<String, Map<String, Object>>();
    private final ExceptionMappers exceptionMappers;
    private final MetricRegistry metricRegistry;
    private final String resourcePath;
    private final String metricName;
    private final String httpMethod;

    public TimedResourceInterceptor(final ExceptionMappers exceptionMappers,
                                    final MetricRegistry metricRegistry,
                                    final String resourcePath,
                                    final String metricName,
                                    final String httpMethod) {
        this.exceptionMappers = exceptionMappers;
        this.metricRegistry = metricRegistry;
        this.resourcePath = resourcePath;
        this.metricName = metricName;
        this.httpMethod = httpMethod;
    }

    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        final long startTime = System.nanoTime();
        int responseStatus = 0;
        try {
            final Object response = invocation.proceed();
            if (response instanceof Response) {
                responseStatus = ((Response) response).getStatus();
            } else if (response == null || response instanceof Void) {
                responseStatus = Response.Status.NO_CONTENT.getStatusCode();
            } else {
                responseStatus = Response.Status.OK.getStatusCode();
            }

            return response;
        } catch (final WebApplicationException e) {
            responseStatus = e.getResponse().getStatus();

            throw e;
        } catch (final Throwable e) {
            responseStatus = mapException(e);

            throw e;
        } finally {
            final long endTime = System.nanoTime();

            final ResourceTimer timer = timer(invocation);
            timer.update(responseStatus, endTime - startTime, TimeUnit.NANOSECONDS);
        }
    }

    private int mapException(final Throwable e) throws Exception {
        final ExceptionMapper<Throwable> exceptionMapper = (exceptionMappers != null) ? exceptionMappers.findMapping(e) : null;

        if (exceptionMapper != null) {
            return exceptionMapper.toResponse(e).getStatus();
        }
        // If there's no mapping for it, assume 500
        return Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
    }

    private ResourceTimer timer(final MethodInvocation invocation) {
        final Map<String, Object> metricTags = metricTags(invocation);

        return new ResourceTimer(resourcePath, metricName, httpMethod, metricTags, metricRegistry);
    }

    private Map<String, Object> metricTags(final MethodInvocation invocation) {
        final Method method = invocation.getMethod();
        // Expensive to compute
        final String methodString = method.toString();

        // Cache metric tags as Method.getParameterAnnotations() generates lots of garbage objects
        Map<String, Object> metricTags = metricTagsByMethod.get(methodString);
        if (metricTags != null) {
            return metricTags;
        }

        metricTags = new LinkedHashMap<String, Object>();
        final Annotation[][] parametersAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < parametersAnnotations.length; i++) {
            final Annotation[] parameterAnnotations = parametersAnnotations[i];

            final MetricTag metricTag = findMetricTagAnnotations(parameterAnnotations);
            if (metricTag != null) {
                final Object currentArgument = invocation.getArguments()[i];
                final Object tagValue;
                if (metricTag.property().trim().isEmpty()) {
                    tagValue = currentArgument;
                } else {
                    tagValue = getProperty(currentArgument, metricTag.property());
                }
                metricTags.put(metricTag.tag(), tagValue);
            }
        }
        metricTagsByMethod.put(methodString, metricTags);

        return metricTags;
    }

    private static MetricTag findMetricTagAnnotations(final Annotation[] parameterAnnotations) {
        for (final Annotation parameterAnnotation : parameterAnnotations) {
            if (parameterAnnotation instanceof MetricTag) {
                return (MetricTag) parameterAnnotation;
            }
        }
        return null;
    }

    private static Object getProperty(final Object currentArgument, final String property) {
        if (currentArgument == null) {
            return null;
        }

        try {
            final String[] methodNames = {"get" + capitalize(property), "is" + capitalize(property), property};
            Method propertyMethod = null;
            for (final String methodName : methodNames) {
                try {
                    propertyMethod = currentArgument.getClass().getMethod(methodName);
                    break;
                } catch (final NoSuchMethodException e) {}
            }
            if (propertyMethod == null) {
                throw handleReadPropertyError(currentArgument, property, null);
            }
            return propertyMethod.invoke(currentArgument);
        } catch (final IllegalAccessException e) {
            throw handleReadPropertyError(currentArgument, property, e);
        } catch (final InvocationTargetException e) {
            throw handleReadPropertyError(currentArgument, property, e);
        }
    }

    private static String capitalize(final String property) {
        return property.substring(0, 1).toUpperCase() + property.substring(1);
    }

    private static IllegalArgumentException handleReadPropertyError(final Object object, final String property, final Exception e) {
        return new IllegalArgumentException(String.format("Failed to read tag property \"%s\" value from object of type %s", property, object.getClass()), e);
    }
}
