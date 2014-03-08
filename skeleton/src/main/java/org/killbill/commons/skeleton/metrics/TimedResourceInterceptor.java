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

import java.util.concurrent.TimeUnit;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * A method interceptor which times the execution of the annotated resource method.
 */
public class TimedResourceInterceptor implements MethodInterceptor {

    private final TimedResourceMetric timer;

    public TimedResourceInterceptor(final TimedResourceMetric timer) {
        this.timer = timer;
    }

    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        final long startTime = System.nanoTime();
        // two ways to get the response code:
        // * response object is returned
        // * exception is thrown
        // * default response code (not sure how to get that ...)
        Integer status = null;

        try {
            final Object result = invocation.proceed();

            if (result instanceof Response) {
                status = ((Response) result).getStatus();
            }
            return result;
        } catch (final WebApplicationException ex) {
            status = ex.getResponse().getStatus();
            throw ex;
        } finally {
            timer.update(status, System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }
}
