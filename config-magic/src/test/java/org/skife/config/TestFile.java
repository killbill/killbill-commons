package org.skife.config;

import java.io.File;
import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestFile
{
    private ConfigurationObjectFactory cof;

    @Before
    public void setUp()
    {
        cof = new ConfigurationObjectFactory(new Properties() {{
            setProperty("file2", "..");
        }});
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

        Assert.assertEquals(new File("."), ec.getFile());
    }

    @Test
    public void testAbstractClassDefault()
    {
    	EmptyAbstractClass ec = cof.build(EmptyAbstractClass.class);

        Assert.assertEquals(new File(".."), ec.getFile());
    }

    @Test
    public void testAbstractClassDefaultNull()
    {
    	EmptyAbstractClassDefaultNull ec = cof.build(EmptyAbstractClassDefaultNull.class);

        Assert.assertNull(ec.getFile());
    }

    public static class EmptyClass
    {
        @Config("file1")
        @Default(".")
        public File getFile()
        {
            return null;
        }
    }

    public abstract static class EmptyAbstractClass
    {
        @Config("file2")
        public abstract File getFile();
    }

    public abstract static class EmptyAbstractClassDefaultNull
    {
        @Config("file3")
        @DefaultNull
        public abstract File getFile();
    }
}
