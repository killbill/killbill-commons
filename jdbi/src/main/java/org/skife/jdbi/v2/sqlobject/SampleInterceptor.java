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

import java.util.concurrent.Callable;

import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

/**
 * This interceptor's only purpose is to invoke the actual method using ByteBuddy.
 */
public class SampleInterceptor {

    @RuntimeType
    public static Object intercept(@SuperCall Callable<?> superCall) throws Exception {
        return superCall.call();
    }

/*    @Advice.OnMethodEnter(suppress = Throwable.class)
    static long enter(@Advice.Origin String origin,
                      @Advice.Origin("#t #m") String detaildOrigin,
                      @Advice.AllArguments Object[] ary){

        System.out.println("Inside enter method . . .  ");

        System.out.println("Origin :" + origin);
        System.out.println("Detailed Origin :" + detaildOrigin);

        return System.nanoTime();
    }*/
}
