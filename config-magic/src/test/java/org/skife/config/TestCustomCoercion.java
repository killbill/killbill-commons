package org.skife.config;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

public class TestCustomCoercion
{
    @Test(expected=IllegalStateException.class)
    public void testNoConverterConfig()
    {
        ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("the-url", "http://github.org/brianm/config-magic");
        }});

        c.build(WibbleConfig.class);
    }

    @Test
    public void testWithConverterConfig()
    {
        ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("the-url", "http://github.org/brianm/config-magic");
        }});

        c.addCoercible(new WibbleCoercible());

        WibbleConfig wc = c.build(WibbleConfig.class);

        Assert.assertThat(wc, is(notNullValue()));

        Wibble w = wc.getWibble();
        Assert.assertThat(w, is(notNullValue()));

        Assert.assertThat(w.getURL(), equalTo("http://github.org/brianm/config-magic"));
    }

    private static class WibbleCoercible implements Coercible<Wibble>
    {
        public Coercer<Wibble> accept(Class<?> clazz)
        {
            if (Wibble.class.equals(clazz)) {
                return new Coercer<Wibble>() {
                    public Wibble coerce(final String value) {
                        Wibble w = new Wibble();
                        w.setURL(value);

                        return w;
                    }
                };
            }
            return null;
        }
    }
}
