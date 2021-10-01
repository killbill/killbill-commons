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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(ConfigMagicTests.class)
public class TestExposeMappedReplacements {

    @Test
    public void testExposeReplacements() {
        final Properties properties = new Properties();
        properties.put("wat.1", "xyzzy");

        final ConfigurationObjectFactory factory = new ConfigurationObjectFactory(properties);
        final Map<String, String> map = new HashMap<String, String>();

        map.put("a", "1");
        map.put("b", "2");

        final ReplacementConfig config = factory.buildWithReplacements(ReplacementConfig.class, map);
        assertEquals("xyzzy", config.getWat());
        assertEquals(map, config.getMap());
    }

    @Test
    public void testNoReplacements() {
        final ConfigurationObjectFactory factory = new ConfigurationObjectFactory(new Properties());

        final ReplacementConfig config = factory.build(ReplacementConfig.class);
        assertTrue(config.getMap().isEmpty());
    }

    @Test
    public void testKeyReplacement() {
        final ConfigurationObjectFactory factory = new ConfigurationObjectFactory(new Properties());
        final Map<String, String> map = new HashMap<String, String>();

        map.put("a", "1");
        map.put("b", "2");

        final ReplacementConfig config = factory.buildWithReplacements(ReplacementConfig.class, map);
        assertEquals("1", config.getAString());
        assertEquals(2, config.getBInt());
    }

    @Test
    public void testDefaultValues() {
        final ConfigurationObjectFactory factory = new ConfigurationObjectFactory(new Properties());
        final ReplacementConfig config = factory.build(ReplacementConfig.class);
        assertEquals(null, config.getDefaultNull());
        assertEquals(3, config.getDefault3());
    }

    public interface ReplacementConfig {

        @Config("wat.${a}")
        @DefaultNull
        String getWat();

        @ConfigReplacements
        Map<String, String> getMap();

        @ConfigReplacements("a")
        @Default("invalid")
        String getAString();

        @ConfigReplacements("b")
        @Default("999")
        int getBInt();

        @ConfigReplacements("x")
        @DefaultNull
        String getDefaultNull();

        @ConfigReplacements("y")
        @Default("3")
        int getDefault3();
    }
}
