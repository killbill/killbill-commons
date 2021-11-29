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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

class ToStringHandler implements Handler
{
    private final String className;

    ToStringHandler(String className)
    {
        this.className = className;
    }

    @Override
    public Object invoke(final HandleDing h, final Object target, final Object[] args, final Callable<?> methodProxy)
    {
        return className + '@' + Integer.toHexString(target.hashCode());
    }

    static Map<Method, Handler> handler(String className)
    {
        try
        {
            Map<Method, Handler> handler = new HashMap<Method, Handler>();
            handler.put(Object.class.getMethod("toString"), new ToStringHandler(className));
            return handler;
        }
        catch (NoSuchMethodException e)
        {
            throw new IllegalStateException("JVM error");
        }
    }

}
