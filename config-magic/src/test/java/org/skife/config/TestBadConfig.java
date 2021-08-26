package org.skife.config;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestBadConfig
{
    ConfigurationObjectFactory c = null;

    @Before
    public void setUp()
    {
        this.c = new ConfigurationObjectFactory(new Properties());
    }

    @After
    public void tearDown()
    {
        this.c = null;
    }

    @Test(expected=IllegalArgumentException.class)
    public void testBadConfig()
    {
        BadConfig bc = c.build(BadConfig.class);
    }
}
