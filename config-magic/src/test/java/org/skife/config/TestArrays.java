package org.skife.config;

import java.util.Properties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestArrays
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

        Assert.assertArrayEquals(new String[] { "one", "three", "two" }, ec.getValue());
    }

    @Test
    public void testAbstractClassDefault()
    {
    	EmptyAbstractClass ec = cof.build(EmptyAbstractClass.class);

        Assert.assertArrayEquals(new TestEnum[] { TestEnum.TWO, TestEnum.ONE }, ec.getValue());
    }

    @Test
    public void testInterface()
    {
        EmptyInterface ec = cof.build(EmptyInterface.class);

        Assert.assertArrayEquals(new float[] { 1.0f, 2.0f }, ec.getValue(), 0.0f);
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

    @Test
    public void testInterfaceDefaultEmptyString()
    {
        EmptyInterfaceEmptyString ec = cof.build(EmptyInterfaceEmptyString.class);

        Assert.assertArrayEquals(new int[0], ec.getValue());
    }

    @Test
    public void testDifferentSeparator()
    {
        DifferentSeparator ec = cof.build(DifferentSeparator.class);

        Assert.assertArrayEquals(new float[] { 1.0f, 2.0f }, ec.getValue(), 0.0f);
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
        @Default("one, three, two")
        public String[] getValue()
        {
            return null;
        }
    }

    public static abstract class EmptyAbstractClass
    {
        @Config("value")
        @Default("TWO, ONE")
        public abstract TestEnum[] getValue();
    }

    public static interface EmptyInterface
    {
        @Config("value")
        @Default("1.0, 2.0")
        public float[] getValue();
    }

    public static class EmptyClassDefaultNull
    {
        @Config("value")
        @DefaultNull
        public int[] getValue()
        {
            return null;
        }
    }

    public static abstract class EmptyAbstractClassDefaultNull
    {
        @Config("value")
        @DefaultNull
        public String[] getValue()
        {
            return null;
        }
    }

    public static interface EmptyInterfaceDefaultNull
    {
        @Config("value")
        @DefaultNull
        public TestEnum[] getValue();
    }

    public static interface EmptyInterfaceEmptyString
    {
        @Config("value")
        @Default("")
        public int[] getValue();
    }

    public static interface DifferentSeparator
    {
        @Config("value")
        @Separator("\\s*;\\s*")
        @Default("1.0 ; 2.0")
        public float[] getValue();
    }
}
