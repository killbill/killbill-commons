package org.skife.config;

import static org.hamcrest.CoreMatchers.is;

import java.net.URI;
import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestCoercion
{
    private ConfigurationObjectFactory c = null;

    @Before
    public void setUp()
    {
        this.c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("the-url", "http://github.org/brianm/config-magic");
        }});
    }

    @After
    public void tearDown()
    {
        this.c = null;
    }

    @Test(expected=IllegalStateException.class)
    public void testBadConfig()
    {
        c.build(WibbleConfig.class);
    }

    @Test
    public void testGoodConfig()
    {
        CoercionConfig cc = c.build(CoercionConfig.class);
        Assert.assertThat(cc.getURI(), is(URI.create("http://github.org/brianm/config-magic")));
    }

    @Test
    public void testEmptyURI()
    {
        final EmptyUriConfig euc1 = new EmptyUriConfig() {};
        Assert.assertNull(euc1.getTheUri());

        final EmptyUriConfig euc2 = c.build(EmptyUriConfig.class);
        Assert.assertNull(euc2.getTheUri());
    }

    public static abstract class EmptyUriConfig
    {
        @Config("the-uri")
        @DefaultNull
        public URI getTheUri()
        {
            return null;
        }
    }

    @Test
    public void testNullDouble()
    {
        final NullDoubleConfig ndc1 = new NullDoubleConfig() {};
        Assert.assertNull(ndc1.getTheNumber());

        final NullDoubleConfig ndc2 = c.build(NullDoubleConfig.class);
        Assert.assertNull(ndc2.getTheNumber());
    }


    public static abstract class NullDoubleConfig
    {
        @Config("the-number")
        @DefaultNull
        public Double getTheNumber()
        {
            return null;
        }
    }
}
