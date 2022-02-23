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
 * A method interceptor which creates a meter for the declaring class with the given name (or the method's name, if none
 * was provided), and which measures the rate at which the annotated method is invoked.
 */
class MeteredInterceptor implements MethodInterceptor {

    private final Meter meter;

    MeteredInterceptor(final Meter meter) {
        this.meter = meter;
    }

    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        meter.mark();
        return invocation.proceed();
    }
}
