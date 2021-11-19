/*
 * Copyright 2004-2014 Brian McCallister
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
package org.skife.jdbi.v2.sqlobject;

import java.lang.reflect.Method;

class PassThroughHandler implements Handler
{

    private final Method method;

    PassThroughHandler(Method method)
    {
        this.method = method;
    }

    @Override
    public Object invoke(HandleDing h, Object target, Object[] args)
    {
        try {
            // Signal the SqlObjectInterceptor.class to invoke the super method.
            return null;
        }
        catch (AbstractMethodError e) {
            throw new AbstractMethodError("Method " + method.getDeclaringClass().getName() + "#" + method.getName() +
                                               " doesn't make sense -- it probably needs a @Sql* annotation of some kind.");
        }
        catch (Throwable throwable) {
            /*

             */
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            }
            else if (throwable instanceof Error) {
                throw (Error) throwable;
            }
            else {
                throw new RuntimeException(throwable);
            }
        }
    }
}
