/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.skife.jdbi.v2.sqlobject;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.SuperMethod;
import net.bytebuddy.implementation.bind.annotation.This;

public class SqlObjectInterceptor {

    SqlObject so;

    public SqlObjectInterceptor(final SqlObject so) {this.so = so;}

    /**
     * This method intercepts all abstract methods with any annotations.
     */
    @RuntimeType
    public Object intercept(@This Object target,
                            @Origin Method method,
                            @SuperMethod(nullIfImpossible = true) Method superMethod,
                            @AllArguments Object[] args) throws Throwable {

        // Method proxy is null as super method invocation is not needed for abstract methods.
        return so.invoke(target, method, args, null);
    }

    /**
     * This method intercepts all non-abstract methods.
     * It passes callable method proxy reference to downstream logic so that the actual super method can be invoked.
     */
    @RuntimeType
    public Object intercept(@Origin Method method,
                            @SuperCall Callable<?> methodProxy,
                            @SuperMethod(nullIfImpossible = true) Method superMethod,
                            @This Object target,
                            @AllArguments Object[] args) throws Throwable {

        return so.invoke(target, method, args, methodProxy);
    }
}