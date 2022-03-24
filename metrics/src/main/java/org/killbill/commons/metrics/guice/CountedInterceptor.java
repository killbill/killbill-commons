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

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.killbill.commons.metrics.api.Counter;
import org.killbill.commons.metrics.api.annotation.Counted;

class CountedInterceptor implements MethodInterceptor {

    private final Counter counter;
    private final boolean decrementAfterMethod;

    CountedInterceptor(final Counter counter, final Counted annotation) {
        this.counter = counter;
        decrementAfterMethod = !annotation.monotonic();
    }

    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        counter.inc(1);
        try {
            return invocation.proceed();
        } finally {
            if (decrementAfterMethod) {
                counter.dec(1);
            }
        }
    }
}
