package org.skife.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;

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
