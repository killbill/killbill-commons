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

package org.skife.config;

import java.util.Arrays;

/**
 * Do case insensitive string comparisons for determination of enum value matches.
 */
public class CaseInsensitiveEnumCoercible implements Coercible<Object>
{
    public Coercer<Object> accept(final Class<?> clazz)
    {
        if (!clazz.isEnum()) {
            return null;
        }

        final Enum<?>[] values;
        try {
            values = (Enum[]) clazz.getMethod("values").invoke(null);
        }
        catch (Exception e) {
            throw new IllegalStateException("World seems to be broken, unable to access <EnumType>.values() static method", e);
        }

        return new Coercer<Object>()
        {
            public Object coerce(String value)
            {
                if (value == null) {
                    return null;
                }
                for (Object o : values) {
                    if (value.equalsIgnoreCase(o.toString())) {
                        return o;
                    }
                }
                throw new IllegalStateException("No enum value of " + Arrays.toString(values) + " matches [" + value + "]");
            }
        };
    }
}
