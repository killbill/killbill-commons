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

package org.killbill.commons.utils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * (At the time of writing), "TypeToken" name chosen because this class try to mimic Guava's TypeToken behavior.
 */
public final class TypeToken {

    /**
     * Mimic the same behavior of Guava's {@code TypeToken.of(clazz).getTypes().rawTypes()}.
     */
    public static Set<Class<?>> getRawTypes(final Class<?> clazz) {
        final Set<Class<?>> result = new LinkedHashSet<>();
        result.add(clazz);
        result.addAll(getInterfaces(clazz));

        Class<?> superClass = clazz.getSuperclass();
        while (superClass != null) {
            result.addAll(getRawTypes(superClass));
            superClass = superClass.getSuperclass();
        }

        return result;
    }

    static Set<Class<?>> getInterfaces(final Class<?> clazz) {
        final Set<Class<?>> result = new LinkedHashSet<>();

        Set<Class<?>> interfaces = Set.of(clazz.getInterfaces());
        while (!interfaces.isEmpty()) {
            result.addAll(interfaces);
            for (final Class<?> anInterface : interfaces) {
                interfaces = Set.of(anInterface.getInterfaces());
                result.addAll(interfaces);
            }
        }

        return result;
    }
}
