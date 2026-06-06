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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * (At the time of writing), "TypeToken" name chosen because this class try to mimic Guava's TypeToken behavior.
 */
public final class TypeToken {

    /**
     * Mimic the same behavior of Guava's {@code TypeToken.of(clazz).getTypes().rawTypes()}.
     */
    public static Set<Class<?>> getRawTypes(final Class<?> clazz) {
        final Map<Class<?>, Integer> depths = new HashMap<>();
        collectTypes(clazz, depths);

        final List<Class<?>> types = new ArrayList<>(depths.keySet());
        types.sort((left, right) -> depths.get(right).compareTo(depths.get(left)));

        return Collections.unmodifiableSet(new LinkedHashSet<>(types));
    }

    private static int collectTypes(final Class<?> type, final Map<Class<?>, Integer> depths) {
        final Integer existing = depths.get(type);
        if (existing != null) {
            return existing;
        }

        int aboveMe = type.isInterface() ? 1 : 0;

        for (final Class<?> interfaceType : type.getInterfaces()) {
            aboveMe = Math.max(aboveMe, collectTypes(interfaceType, depths));
        }

        final Class<?> superclass = type.getSuperclass();
        if (superclass != null) {
            aboveMe = Math.max(aboveMe, collectTypes(superclass, depths));
        }

        final int depth = aboveMe + 1;
        depths.put(type, depth);
        return depth;
    }

}
