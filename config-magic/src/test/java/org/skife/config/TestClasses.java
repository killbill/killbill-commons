package org.skife.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

public class TestClasses
{
    @Test
    public void testRawType()
    {
        WithRawType config = new ConfigurationObjectFactory(Props.of("theClazz", Object.class.getName())).build(WithRawType.class);

        Assert.assertEquals(config.getTheClazz(), Object.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRawTypeNotFound()
    {
        new ConfigurationObjectFactory(Props.of("theClazz", "does.not.Exist")).build(WithRawType.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRawTypeIllegal()
    {
        new ConfigurationObjectFactory(Props.of("theClazz", "not a class")).build(WithRawType.class);
    }

    @Test
    public void testRawTypeWithDefault()
    {
        WithRawTypeAndDefault config = new ConfigurationObjectFactory(new Properties()).build(WithRawTypeAndDefault.class);

        Assert.assertEquals(config.getTheClazz(), Object.class);
    }

    @Test
    public void testRawTypeWithNullDefault()
    {
        WithRawType config = new ConfigurationObjectFactory(new Properties()).build(WithRawType.class);

        Assert.assertNull(config.getTheClazz());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRawTypeWithNotFoundDefault()
    {
        new ConfigurationObjectFactory(new Properties()).build(WithRawTypeAndUndefinedDefault.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRawTypeWithIllegalDefault()
    {
        new ConfigurationObjectFactory(new Properties()).build(WithRawTypeAndIllegalDefault.class);
    }

    @Test
    public void testUnspecifiedType()
    {
        WithUnspecifiedType config = new ConfigurationObjectFactory(Props.of("theClazz", Foo.class.getName())).build(WithUnspecifiedType.class);

        Assert.assertEquals(config.getTheClazz(), Foo.class);
    }

    @Test
    public void testExtends()
    {
        WithExtends config = new ConfigurationObjectFactory(Props.of("theClazz", Foo.class.getName())).build(WithExtends.class);

        Assert.assertEquals(config.getTheClazz(), Foo.class);
    }

    @Test
    public void testExtendsWithSubClass()
    {
        WithExtends config = new ConfigurationObjectFactory(Props.of("theClazz", FooSub.class.getName())).build(WithExtends.class);

        Assert.assertEquals(config.getTheClazz(), FooSub.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtendsWithSuperClass()
    {
        new ConfigurationObjectFactory(Props.of("theClazz", FooSuper.class.getName())).build(WithExtends.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtendsWithUnrelatedClass()
    {
        new ConfigurationObjectFactory(Props.of("theClazz", Properties.class.getName())).build(WithExtends.class);
    }

    @Test
    public void testNestedExtends()
    {
        WithNestedExtends config = new ConfigurationObjectFactory(Props.of("theClazz", FooList.class.getName())).build(WithNestedExtends.class);

        Assert.assertEquals(config.getTheClazz(), FooList.class);
    }

    @Test
    public void testNestedExtendsWithSubClass()
    {
        WithNestedExtends config = new ConfigurationObjectFactory(Props.of("theClazz", FooSubList.class.getName())).build(WithNestedExtends.class);

        Assert.assertEquals(config.getTheClazz(), FooSubList.class);
    }

    @Test
    public void testNestedExtendsWithSuperClass()
    {
        WithNestedExtends config = new ConfigurationObjectFactory(Props.of("theClazz", FooSuperList.class.getName())).build(WithNestedExtends.class);

        Assert.assertEquals(config.getTheClazz(), FooSuperList.class);
    }

    @Test
    public void testNestedExtendsWithUnrelatedClass()
    {
        WithNestedExtends config = new ConfigurationObjectFactory(Props.of("theClazz", StringList.class.getName())).build(WithNestedExtends.class);

        Assert.assertEquals(config.getTheClazz(), StringList.class);
    }

    public static interface WithRawType
    {
        @SuppressWarnings("rawtypes")
        @Config("theClazz")
        @DefaultNull
        Class getTheClazz();
    }

    public static interface WithRawTypeAndDefault
    {
        @SuppressWarnings("rawtypes")
        @Config("theClazz")
        @Default("java.lang.Object")
        Class getTheClazz();
    }

    public static interface WithRawTypeAndUndefinedDefault
    {
        @SuppressWarnings("rawtypes")
        @Config("theClazz")
        @Default("does.not.Exist")
        Class getTheClazz();
    }

    public static interface WithRawTypeAndIllegalDefault
    {
        @SuppressWarnings("rawtypes")
        @Config("theClazz")
        @Default("not a class")
        Class getTheClazz();
    }

    public static interface WithUnspecifiedType
    {
        @Config("theClazz")
        Class<?> getTheClazz();
    }

    public static interface WithExtends
    {
        @Config("theClazz")
        Class<? extends Foo> getTheClazz();
    }

    public static interface WithNestedExtends
    {
        @Config("theClazz")
        Class<? extends List<? extends Foo>> getTheClazz();
    }

    public static class FooSuper {} 
    public static class Foo extends FooSuper {} 
    public static class FooSub extends Foo {} 

    @SuppressWarnings("serial")
    public static class FooSuperList extends ArrayList<FooSuper> {};
    @SuppressWarnings("serial")
    public static class FooList extends ArrayList<Foo> {};
    @SuppressWarnings("serial")
    public static class FooSubList extends ArrayList<FooSub> {};
    @SuppressWarnings("serial")
    public static class StringList extends ArrayList<String> {};
}
