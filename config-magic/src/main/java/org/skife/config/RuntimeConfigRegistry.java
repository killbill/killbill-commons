/*
 * Copyright 2020-2025 Equinix, Inc
 * Copyright 2014-2025 The Billing Project, LLC
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry to capture and expose runtime configuration values.
 */
public class RuntimeConfigRegistry {

    private static final Map<String, Map<String, String>> RUNTIME_CONFIGS_BY_SOURCE = new ConcurrentHashMap<>();

    public static void putWithSource(final String configSource, final String key, final Object value) {
        RUNTIME_CONFIGS_BY_SOURCE
                .computeIfAbsent(configSource, k -> new ConcurrentHashMap<>())
                .put(key, value == null ? "" : value.toString());
    }

    public static void putAllWithSource(final String configSource, final Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return;
        }

        RUNTIME_CONFIGS_BY_SOURCE
                .computeIfAbsent(configSource, k -> new ConcurrentHashMap<>())
                .putAll(values.entrySet()
                              .stream()
                              .collect(Collectors.toMap(Map.Entry::getKey,
                                                        e -> e.getValue() == null ? "" : e.getValue().toString())));
    }

    public static String get(final String key) {
        for (final Map<String, String> sourceMap : RUNTIME_CONFIGS_BY_SOURCE.values()) {
            final String value = sourceMap.get(key);
            if (value != null) {
                return value;
            }
        }

        return "";
    }

    public static Map<String, String> getBySource(final String source) {
        return Collections.unmodifiableMap(RUNTIME_CONFIGS_BY_SOURCE.getOrDefault(source, Map.of()));
    }

    public static Map<String, String> getAll() {
        final Map<String, String> allConfigs = new LinkedHashMap<>();
        RUNTIME_CONFIGS_BY_SOURCE.values().forEach(allConfigs::putAll);

        return Collections.unmodifiableMap(allConfigs);
    }

    public static Map<String, Map<String, String>> getAllBySource() {
        return Collections.unmodifiableMap(RUNTIME_CONFIGS_BY_SOURCE);
    }

    public static void clear() {
        RUNTIME_CONFIGS_BY_SOURCE.clear();
    }
}
