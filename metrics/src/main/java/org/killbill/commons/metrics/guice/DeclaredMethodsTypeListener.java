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

import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

/**
 * A TypeListener which delegates to {@link DeclaredMethodsTypeListener#getInterceptor(Method)} for each method in the
 * class's declared methods.
 */
abstract class DeclaredMethodsTypeListener implements TypeListener {

    @Override
    public <T> void hear(final TypeLiteral<T> literal, final TypeEncounter<T> encounter) {
        final Class<? super T> klass = literal.getRawType();

        for (final Method method : klass.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }

            final MethodInterceptor interceptor = getInterceptor(method);
            if (interceptor != null) {
                encounter.bindInterceptor(Matchers.only(method), interceptor);
            }
        }
    }

    /**
     * Called for every method on every class in the type hierarchy of the visited type
     *
     * @param method method to get interceptor for
     * @return null if no interceptor should be applied, else an interceptor
     */
    @Nullable
    protected abstract MethodInterceptor getInterceptor(Method method);
}
