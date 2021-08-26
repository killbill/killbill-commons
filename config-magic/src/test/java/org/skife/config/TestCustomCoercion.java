/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.skife.config;

import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@Category(ConfigMagicTests.class)
public class TestCustomCoercion {

    @Test(expected = IllegalStateException.class)
    public void testNoConverterConfig() {
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("the-url", "http://github.org/brianm/config-magic");
        }});

        c.build(WibbleConfig.class);
    }

    @Test
    public void testWithConverterConfig() {
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("the-url", "http://github.org/brianm/config-magic");
        }});

        c.addCoercible(new WibbleCoercible());

        final WibbleConfig wc = c.build(WibbleConfig.class);

        Assert.assertThat(wc, is(notNullValue()));

        final Wibble w = wc.getWibble();
        Assert.assertThat(w, is(notNullValue()));

        Assert.assertThat(w.getURL(), equalTo("http://github.org/brianm/config-magic"));
    }

    private static class WibbleCoercible implements Coercible<Wibble> {

        public Coercer<Wibble> accept(final Class<?> clazz) {
            if (Wibble.class.equals(clazz)) {
                return new Coercer<Wibble>() {
                    public Wibble coerce(final String value) {
                        final Wibble w = new Wibble();
                        w.setURL(value);

                        return w;
                    }
                };
            }
            return null;
        }
    }
}
