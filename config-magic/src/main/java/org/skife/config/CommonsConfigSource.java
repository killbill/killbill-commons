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

import org.apache.commons.configuration.Configuration;

public class CommonsConfigSource implements ConfigSource {

    private final Configuration config;

    public CommonsConfigSource(final Configuration config) {
        this.config = config;
    }

    public String getString(final String propertyName) {
        final String[] strings = config.getStringArray(propertyName);
        if (strings == null || strings.length == 0) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < strings.length; i++) {
            sb.append(strings[i]);
            if (i < strings.length - 1) {
                sb.append(',');
            }
        }
        return sb.toString();
    }
}
