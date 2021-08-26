package org.skife.config;

import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDataAmount
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
    public void testKiloBytes()
    {
        DataAmount amount = new DataAmount("20kB");
        Assert.assertEquals(DataAmountUnit.KILOBYTE, amount.getUnit());
        Assert.assertEquals(20L * 1000, amount.getNumberOfBytes());
        // and space allowed now as well
        amount = new DataAmount("20 kB");
        Assert.assertEquals(DataAmountUnit.KILOBYTE, amount.getUnit());
        Assert.assertEquals(20L * 1000, amount.getNumberOfBytes());
        
        ClassWithKilobytes ec = cof.build(ClassWithKilobytes.class);

        Assert.assertEquals(DataAmountUnit.KILOBYTE, ec.getValue().getUnit());
        Assert.assertEquals(10L * 1000, ec.getValue().getNumberOfBytes());
    }

    @Test
    public void testRawBytes()
    {
        DataAmount amt = new DataAmount("1024");
        Assert.assertEquals(DataAmountUnit.BYTE, amt.getUnit());
        Assert.assertEquals(1024L, amt.getNumberOfBytes());

        amt = new DataAmount(2000);
        Assert.assertEquals(DataAmountUnit.BYTE, amt.getUnit());
        Assert.assertEquals(2000L, amt.getNumberOfBytes());
    }

    public static abstract class ClassWithKilobytes
    {
        @Config("value")
        @Default("10kB")
        public abstract DataAmount getValue();
    }
}
