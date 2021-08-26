package org.skife.config;

import static org.hamcrest.CoreMatchers.is;

import java.util.Collections;
import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestMultiConfig
{
    ConfigurationObjectFactory c = null;

    @Before
    public void setUp()
    {
        this.c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("singleOption", "the-single-value");
            setProperty("multiOption1", "the-multi-option1-value");
            setProperty("multiOption2", "the-multi-option2-value");
            setProperty("fooExtOption", "the-fooExt-option-value");
            setProperty("barExtOption", "the-barExt-option-value");
            setProperty("defaultOption", "the-default-option-value");
        }});
    }

    @After
    public void tearDown()
    {
        this.c = null;
    }

    @Test
    public void testSimple()
    {
        MultiConfig mc = c.build(MultiConfig.class);
        Assert.assertThat(mc.getSingleOption(), is("the-single-value"));
    }

    @Test
    public void testSimple2()
    {
        MultiConfig mc = c.build(MultiConfig.class);
        Assert.assertThat(mc.getSingleOption2(), is("the-single-value"));
    }

    @Test
    public void testMultiOption1()
    {
        MultiConfig mc = c.build(MultiConfig.class);
        Assert.assertThat(mc.getMultiOption1(), is("the-multi-option1-value"));
    }

    @Test
    public void testMultiOption2()
    {
        MultiConfig mc = c.build(MultiConfig.class);
        Assert.assertThat(mc.getMultiOption2(), is("the-multi-option2-value"));
    }

    @Test
    public void testMultiDefault()
    {
        MultiConfig mc = c.build(MultiConfig.class);
        Assert.assertThat(mc.getMultiDefault(), is("theDefault"));
    }

    @Test
    public void testMultiReplace1()
    {
        MultiConfig mc = c.buildWithReplacements(MultiConfig.class, Collections.singletonMap("key", "foo"));
        Assert.assertThat(mc.getReplaceOption(), is("the-fooExt-option-value"));
    }

    @Test
    public void testMultiReplace2()
    {
        MultiConfig mc = c.buildWithReplacements(MultiConfig.class, Collections.singletonMap("key", "bar"));
        Assert.assertThat(mc.getReplaceOption(), is("the-barExt-option-value"));
    }

    @Test
    public void testMultiReplaceDefault()
    {
        MultiConfig mc = c.buildWithReplacements(MultiConfig.class, Collections.singletonMap("key", "baz"));
        Assert.assertThat(mc.getReplaceOption(), is("the-default-option-value"));
    }

}
