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

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Replacement of Guava's {@code Joiner.MapJoiner}.
 */
public final class MapJoiner {

    private final String separator;
    private final String keyValueSeparator;

    public MapJoiner(final String separator, final String keyValueSeparator) {
        this.separator = separator;
        this.keyValueSeparator = keyValueSeparator;
    }

    public String join(final Map<?, ?> map) {
        final StringBuilder sb = new StringBuilder();
        final Iterator<? extends Entry<?, ?>> iterator = map.entrySet().iterator();
        if (iterator.hasNext()) {
            final Map.Entry<?, ?> entry = iterator.next();
            if (isEntryKeyExist(entry)) {
                sb.append(entry.getKey());
                sb.append(separator);
                sb.append(entry.getValue());
            }

            while (iterator.hasNext()) {
                final Map.Entry<?, ?> e = iterator.next();
                if (sb.length() != 0) {
                    sb.append(keyValueSeparator);
                }

                if (isEntryKeyExist(e)) {
                    sb.append(e.getKey());
                    sb.append(separator);
                    sb.append(e.getValue());
                }
            }
        }
        return sb.toString();
    }

    private boolean isEntryKeyExist(final Entry<?, ?> entry) {
        final Object o = entry.getKey();
        if (o == null) {
            return false;
        }
        final String s = o.toString();

        return !(s.isEmpty() && s.isBlank());
    }
}
