package org.skife.config;

import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestEmptyValue
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

    @Test(expected = IllegalArgumentException.class)
    public void testClass()
    {
        cof.build(EmptyClass.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInterface()
    {
        cof.build(EmptyInterface.class);
    }

    @Test
    public void testDefaultClass()
    {
        EmptyDefaultClass ec = cof.build(EmptyDefaultClass.class);

        Assert.assertEquals("default-value", ec.getValue());
    }

    @Test
    public void testAbstractDefaultClass()
    {
        EmptyAbstractClass ec = cof.build(EmptyAbstractClass.class);

        Assert.assertEquals("default-value", ec.getValue());
    }

    public static interface EmptyInterface
    {
        @Config("value")
        String getValue();
    }

    public static abstract class EmptyClass
    {
        @Config("value")
        public abstract String getValue();
    }

    public static abstract class EmptyAbstractClass
    {
        @Config("value")
        public String getValue()
        {
            return "default-value";
        }
    }


    public static class EmptyDefaultClass
    {
        @Config("value")
        public String getValue()
        {
            return "default-value";
        }
    }
}

