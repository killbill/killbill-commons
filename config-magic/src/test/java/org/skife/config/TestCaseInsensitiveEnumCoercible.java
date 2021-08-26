package org.skife.config;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class TestCaseInsensitiveEnumCoercible
{
    @Test
    public void testHappyPath() throws Exception
    {
        ConfigurationObjectFactory cof = new ConfigurationObjectFactory(Props.of("creamer", "half_and_half"));

        Coffee coffee = cof.build(Coffee.class);
        assertThat(coffee.getCreamer(), equalTo(Creamer.HALF_AND_HALF));
    }


    @Test(expected = IllegalStateException.class)
    public void testNoMatch() throws Exception
    {
        ConfigurationObjectFactory cof = new ConfigurationObjectFactory(Props.of("creamer", "goat_milk"));

        Coffee coffee = cof.build(Coffee.class);
        fail("should have raised an illegal state exception");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExactMatch() throws Exception
    {
        ConfigurationObjectFactory cof = new ConfigurationObjectFactory(Props.of("creamer", "whole_milk"));
        cof.addCoercible(new ExactMatchEnumCoercible());

        Coffee coffee = cof.build(Coffee.class);
        fail("should have raised an exception");
    }

    public static abstract class Coffee
    {
        @Config("creamer")
        public abstract Creamer getCreamer();
    }

    public static enum Creamer
    {
        HEAVY_CREAM, HALF_AND_HALF, WHOLE_MILK, SKIM_MILK, GROSS_WHITE_POWDER
    }
}
