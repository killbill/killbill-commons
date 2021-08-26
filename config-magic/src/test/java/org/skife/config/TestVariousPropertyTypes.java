package org.skife.config;

import java.util.Collections;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestVariousPropertyTypes
{
    private ConfigurationObjectFactory c = null;
    private StrangeConfig sc = null;

    @Before
    public void setUp()
    {
        final Properties p = new Properties();
        p.setProperty("single-property", "single-property-value");
        p.setProperty("multi.first.property", "multi-first-value");
        p.setProperty("double.second.property", "double-second-value");
        p.setProperty("test.value.property", "test-value-value");
        p.setProperty("test.default.property", "test-default-value");
        c = new ConfigurationObjectFactory(p);
        sc = c.buildWithReplacements(StrangeConfig.class, Collections.singletonMap("key", "value"));
    }


    @Test
    public void testWithDefault()
    {
        Assert.assertEquals("default-is-set", sc.getHasDefault());
    }

    @Test
    public void testWithDefaultNull()
    {
        Assert.assertNull(sc.getHasDefaultNull());
    }

    @Test
    public void testCallMethod()
    {
        Assert.assertEquals("called getCalledMethod()", sc.getCalledMethod());
    }

    @Test
    public void testMultiProperty()
    {
        Assert.assertEquals("multi-first-value", sc.getMultiProperty());
    }

    @Test
    public void testDoubleProperty()
    {
        Assert.assertEquals("double-second-value", sc.getDoubleProperty());
    }

    @Test
    public void testKeyedProperty()
    {
        Assert.assertEquals("test-value-value", sc.getKeyedProperty());
    }

    public static abstract class StrangeConfig
    {
        @Config("has-default")
        @Default("default-is-set")
        public String getHasDefault()
        {
            return "called getHasDefault()";
        }

        @Config("has-default-null")
        @DefaultNull
        public String getHasDefaultNull()
        {
            return "called getHasDefault()";
        }

        @Config("get-called-method")
        public String getCalledMethod()
        {
            return "called getCalledMethod()";
        }

        @Config("single.property")
        public String getSingleProperty()
        {
            return "called getSingleProperty()";
        }

        @Config({"multi.first.property", "multi.second.property"})
        public String getMultiProperty()
        {
            return "called getMultiProperty()";
        }

        @Config({"double.first.property", "double.second.property"})
        public String getDoubleProperty()
        {
            return "called getDoubleProperty()";
        }

        @Config({"test.${key}.property", "test.default.property"})
        public String getKeyedProperty()
        {
            return "called getKeyedProperty()";
        }
}
}



