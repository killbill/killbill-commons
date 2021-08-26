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

import java.lang.reflect.Method;

public class ExactMatchEnumCoercible implements Coercible<Object> {

    public Coercer<Object> accept(final Class<?> clazz) {
        if (!clazz.isEnum()) {
            return null;
        }
        try {
            final Method m = clazz.getMethod("valueOf", String.class);
            return new Coercer<Object>() {
                public Object coerce(final String value) {
                    if (value == null) {
                        return null;
                    }
                    try {
                        return m.invoke(null, value);
                    } catch (final Exception e) {
                        throw DefaultCoercibles.convertException(e);
                    }
                }
            };
        } catch (final NoSuchMethodException e) {
            throw new IllegalStateException("<EnumType>.valueOf(String) missing! World broken!", e);
        }

    }
}
