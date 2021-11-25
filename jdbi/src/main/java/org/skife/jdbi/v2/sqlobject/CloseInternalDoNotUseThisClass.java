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

/**
 * This is public as we need it to be for some stuff to work. It is an internal api and NOT to be used
 * by users!
 */
public interface CloseInternalDoNotUseThisClass
{
    void ___jdbi_close___();

    static class CloseHandler implements Handler
    {
        @Override
        public Object invoke(HandleDing h, Object target, Object[] args, Callable<?> methodProxy)
        {
            h.getHandle().close();
            return null;
        }
    }

    static class Helper
    {
        static Map<Method, Handler> handlers()
        {
            try {
                Map<Method, Handler> h = new HashMap<Method, Handler>();
                h.put(CloseInternalDoNotUseThisClass.class.getMethod("___jdbi_close___"), new CloseHandler());
                return h;
            }
            catch (NoSuchMethodException e) {
                throw new IllegalStateException("someone wonkered up the bytecode", e);
            }
        }
    }
}
