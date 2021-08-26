package org.skife.config;

import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDefaultNull
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
    public void testClass()
    {
        EmptyClass ec = cof.build(EmptyClass.class);

        Assert.assertNull(ec.getValue());
    }

    @Test
    public void testInterface()
    {
        EmptyInterface ec = cof.build(EmptyInterface.class);

        Assert.assertNull(ec.getValue());
    }


    @Test(expected = IllegalArgumentException.class)
    public void testDoubleFeature()
    {
        cof.build(DoubleFeature.class);
    }

    public static interface EmptyInterface
    {
        @Config("value")
        @DefaultNull
        String getValue();
    }

    public static abstract class EmptyClass
    {
        @Config("value")
        @DefaultNull
        public abstract String getValue();
    }


    public static class DoubleFeature
    {
        @Config("value")
        @DefaultNull
        @Default("value-default")
        public String getValue()
        {
            return "default-value";
        }
    }
}

