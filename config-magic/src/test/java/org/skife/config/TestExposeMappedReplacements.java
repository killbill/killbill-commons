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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(ConfigMagicTests.class)
public class TestExposeMappedReplacements
{

    public static interface ReplacementConfig
    {
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

    @Test
    public void testExposeReplacements()
    {
        Properties properties = new Properties();
        properties.put("wat.1", "xyzzy");

        ConfigurationObjectFactory factory = new ConfigurationObjectFactory(properties);
        Map<String, String> map = new HashMap<String, String>();

        map.put("a", "1");
        map.put("b", "2");

        ReplacementConfig config = factory.buildWithReplacements(ReplacementConfig.class, map);
        assertEquals("xyzzy", config.getWat());
        assertEquals(map, config.getMap());
    }

    @Test
    public void testNoReplacements()
    {
        ConfigurationObjectFactory factory = new ConfigurationObjectFactory(new Properties());

        ReplacementConfig config = factory.build(ReplacementConfig.class);
        assertTrue(config.getMap().isEmpty());
    }

    @Test
    public void testKeyReplacement()
    {
        ConfigurationObjectFactory factory = new ConfigurationObjectFactory(new Properties());
        Map<String, String> map = new HashMap<String, String>();

        map.put("a", "1");
        map.put("b", "2");

        ReplacementConfig config = factory.buildWithReplacements(ReplacementConfig.class, map);
        assertEquals("1", config.getAString());
        assertEquals(2, config.getBInt());
    }

    @Test
    public void testDefaultValues()
    {
        ConfigurationObjectFactory factory = new ConfigurationObjectFactory(new Properties());
        ReplacementConfig config = factory.build(ReplacementConfig.class);
        assertEquals(null, config.getDefaultNull());
        assertEquals(3, config.getDefault3());
    }
}
