package org.skife.config;

import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

public class TestNoFinal
{
    @Test(expected=IllegalArgumentException.class)
    public void testExplodeOnFinal()
    {
        ConfigurationObjectFactory cof = new ConfigurationObjectFactory(new Properties());
        cof.build(EmptyClass.class);
    }

    public static final class EmptyClass
    {
        @Config("value")
        public String getValue()
        {
            return "default-value";
        }
    }
}

