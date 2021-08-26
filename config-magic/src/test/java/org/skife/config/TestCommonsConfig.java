package org.skife.config;

import static junit.framework.Assert.assertEquals;

import java.util.Properties;

import org.apache.commons.configuration.ConfigurationConverter;
import org.junit.Test;

/**
 *
 */
public class TestCommonsConfig
{
    @Test
    public void testFoo() throws Exception {
        Properties props = new Properties();
        props.setProperty("hello", "world");
        final ConfigSource cs = new CommonsConfigSource(ConfigurationConverter.getConfiguration(props));
        assertEquals("world", cs.getString("hello"));
    }

    @Test
    public void testCommas() throws Exception {
        Properties props = new Properties();
        props.setProperty("hello", "world,brian,someone else");
        final ConfigSource cs = new CommonsConfigSource(ConfigurationConverter.getConfiguration(props));
        assertEquals("world,brian,someone else", cs.getString("hello"));
    }

    @Test
    public void testEscapedCommas() throws Exception {
        Properties props = new Properties();
        props.setProperty("hello", "world\\, brian\\, someone else");
        final ConfigSource cs = new CommonsConfigSource(ConfigurationConverter.getConfiguration(props));
        assertEquals("world, brian, someone else", cs.getString("hello"));
    }

    @Test
    public void testEmpty() throws Exception {
        Properties props = new Properties();
        final ConfigSource cs = new CommonsConfigSource(ConfigurationConverter.getConfiguration(props));
        assertEquals(null, cs.getString("hello"));
    }
}
