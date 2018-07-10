/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.commons.profiling;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

public class ProfilingFeature {

    private static final int JAXRS_MASK = 0x1;
    private static final int API_MASK = 0x2;
    private static final int DAO_MASK = 0x4;
    private static final int DAO_DETAILS_MASK = 0x8;
    private static final int GLOCK_MASK = 0x10;
    private static final int PLUGIN_MASK = 0x20;
    private static final int DAO_CONNECTION_MASK = 0x40;

    public enum ProfilingFeatureType {
        JAXRS(JAXRS_MASK),
        API(API_MASK),
        DAO(DAO_MASK),
        DAO_DETAILS(DAO_MASK, DAO_DETAILS_MASK),
        DAO_CONNECTION(DAO_CONNECTION_MASK),
        GLOCK(GLOCK_MASK),
        PLUGIN(PLUGIN_MASK);

        private final int mask;

        ProfilingFeatureType(final int... masks) {
            int tmp = 0;
            for (final int mask1 : masks) {
                tmp |= mask1;
            }
            this.mask = tmp;
        }
        public int getMask() {
            return mask;
        }
    }

    private final Pattern featurePattern = Pattern.compile("\\s*,?\\s*((?:[A-Z])+(?:_)?+(?:[A-Z])*)");

    private final int profilingBits;

    public ProfilingFeature() {
        int tmp = 0;
        for (final ProfilingFeatureType cur : ProfilingFeatureType.values()) {
            tmp |= cur.getMask();
        }
        this.profilingBits = tmp;
    }

    public ProfilingFeature(final String features) {
        int tmp = 0;
        final Matcher matcher = featurePattern.matcher(features);
        while (matcher.find()) {
            final String cur = matcher.group(1);
            try {
                final ProfilingFeatureType featureType = ProfilingFeatureType.valueOf(cur);
                tmp |= featureType.getMask();
            } catch (final IllegalArgumentException e) {
                // Ignore bad entry like 'FOO'
            }
        }
        this.profilingBits = tmp;
    }

    public boolean isDefined(final ProfilingFeatureType type) {
        return (profilingBits & type.getMask()) == type.getMask();
    }

    public boolean isProfilingJAXRS() {
        return isDefined(ProfilingFeatureType.JAXRS);
    }

    public boolean isProfilingAPI() {
        return isDefined(ProfilingFeatureType.API);
    }

    public boolean isProfilingDAO() {
        return isDefined(ProfilingFeatureType.DAO);
    }

    public boolean isProfilingDAOWithDetails() {
        return isDefined(ProfilingFeatureType.DAO_DETAILS);
    }

    public boolean isProfilingPlugin() {
        return isDefined(ProfilingFeatureType.PLUGIN);
    }

    public boolean isProfilingGlock() {
        return isDefined(ProfilingFeatureType.GLOCK);
    }

    public boolean isProfilingDaoConnection() {
        return isDefined(ProfilingFeatureType.DAO_CONNECTION);
    }

}
