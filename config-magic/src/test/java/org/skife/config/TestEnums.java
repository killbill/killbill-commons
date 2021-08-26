package org.skife.config;

import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestEnums
{
    private ConfigurationObjectFactory cof;

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

        Assert.assertEquals(TestEnum.ONE, ec.getValue());
    }

    @Test
    public void testAbstractClassDefault()
    {
    	EmptyAbstractClass ec = cof.build(EmptyAbstractClass.class);

        Assert.assertEquals(TestEnum.TWO, ec.getValue());
    }

    @Test
    public void testInterface()
    {
        EmptyInterface ec = cof.build(EmptyInterface.class);

        Assert.assertEquals(TestEnum.THREE, ec.getValue());
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

    @Test
    public void testInterfaceDefaultNull()
    {
        EmptyInterfaceDefaultNull ec = cof.build(EmptyInterfaceDefaultNull.class);

        Assert.assertNull(ec.getValue());
    }

    public static enum TestEnum
    {
        ONE,
        TWO,
        THREE
    }

    public static class EmptyClass
    {
        @Config("value")
        @Default("ONE")
        public TestEnum getValue()
        {
            return TestEnum.ONE;
        }
    }


    public static abstract class EmptyAbstractClass
    {
        @Config("value")
        @Default("TWO")
        public abstract TestEnum getValue();
    }

    public static class EmptyClassDefaultNull
    {
        @Config("value")
        @DefaultNull
        public TestEnum getValue()
        {
            return TestEnum.THREE;
        }
    }

    public static interface EmptyInterface
    {
        @Config("value")
        @Default("THREE")
        public TestEnum getValue();
    }

    public static abstract class EmptyAbstractClassDefaultNull
    {
        @Config("value")
        @DefaultNull
        public TestEnum getValue()
        {
            return null;
        }
    }

    public static interface EmptyInterfaceDefaultNull
    {
        @Config("value")
        @DefaultNull
        public TestEnum getValue();
    }
}
