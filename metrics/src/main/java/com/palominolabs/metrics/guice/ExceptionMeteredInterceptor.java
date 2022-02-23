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

package com.palominolabs.metrics.guice;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import com.codahale.metrics.Meter;

/**
 * A method interceptor which measures the rate at which the annotated method throws exceptions of a given type.
 */
class ExceptionMeteredInterceptor implements MethodInterceptor {

    private final Meter meter;
    private final Class<? extends Throwable> klass;

    ExceptionMeteredInterceptor(final Meter meter, final Class<? extends Throwable> klass) {
        this.meter = meter;
        this.klass = klass;
    }

    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        try {
            return invocation.proceed();
        } catch (final Throwable t) {
            if (klass.isAssignableFrom(t.getClass())) {
                meter.mark();
            }
            throw t;
        }
    }
}
