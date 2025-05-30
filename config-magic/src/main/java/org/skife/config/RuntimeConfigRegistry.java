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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry to capture and expose runtime configuration values.
 */
public class RuntimeConfigRegistry {

    private static final Map<String, String> RUNTIME_CONFIGS = new ConcurrentHashMap<>();

    public static void put(final String key, final Object value) {
        RUNTIME_CONFIGS.put(key, value == null ? "" : value.toString());
    }

    public static String get(final String key) {
        return RUNTIME_CONFIGS.getOrDefault(key, "");
    }

    public static Map<String, String> getAll() {
        return Collections.unmodifiableMap(RUNTIME_CONFIGS);
    }
}
