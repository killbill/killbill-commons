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

    @RuntimeType
    public Object intercept(@This Object target,
                            @Origin Method method,
                            @SuperMethod(nullIfImpossible = true) Method superMethod,
                            @AllArguments Object[] args) throws Throwable {

        Object handler = so.invoke(target, method, args);

    /*    if (Objects.isNull(handler) && Objects.nonNull(superMethod)) {
            return superMethod.invoke(target, args);
        }

        if (Objects.isNull(superMethod) && Objects.isNull(handler)) {
            throw new AbstractMethodError();
        }*/

        return handler;
    }

    @RuntimeType
    public Object intercept(@Origin Method method,
                            @SuperCall Callable<?> proxy,
                            @SuperMethod(nullIfImpossible = true) Method superMethod,
                            @This Object target,
                            @AllArguments Object[] args) throws Exception {

        return proxy.call();
    }
}