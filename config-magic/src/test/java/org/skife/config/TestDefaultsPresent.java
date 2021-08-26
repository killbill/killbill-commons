package org.skife.config;

import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDefaultsPresent
{
    private ConfigurationObjectFactory cof = null;

    @Before
    public void setUp()
    {
        cof = new ConfigurationObjectFactory(new Properties());
    }

    @After
    public void tearDown()
    {
        cof = null;
    }

    @Test
    public void testClassDefault()
    {
        EmptyClass ec = cof.build(EmptyClass.class);

        Assert.assertEquals("default-value", ec.getValue());
    }

    @Test
    public void testAbstractClassDefault()
    {
    	EmptyAbstractClass ec = cof.build(EmptyAbstractClass.class);

        Assert.assertEquals("default-value", ec.getValue());
    }

    @Test
    public void testClassDefaultNull()
    {
    	EmptyClassDefaultNull ec = cof.build(EmptyClassDefaultNull.class);

        Assert.assertNull(ec.getValue());
    }

    @Test
    public void testAbstractClassDefaultNull()
    {
    	EmptyAbstractClassDefaultNull ec = cof.build(EmptyAbstractClassDefaultNull.class);

        Assert.assertNull(ec.getValue());
    }


    public static class EmptyClass
    {
        @Config("value")
        @Default("default-value")
        public String getValue()
        {
            return "value-default";
        }
    }


    public static abstract class EmptyAbstractClass
    {
        @Config("value")
        @Default("default-value")
        public String getValue()
        {
            return "value-default";
        }
    }


    public static class EmptyClassDefaultNull
    {
        @Config("value")
        @DefaultNull
        public String getValue()
        {
            return "value-default";
        }
    }

    public static abstract class EmptyAbstractClassDefaultNull
    {
        @Config("value")
        @DefaultNull
        public String getValue()
        {
            return "value-default";
        }
    }
}
