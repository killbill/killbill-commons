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

import java.net.URI;
import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.hamcrest.CoreMatchers.is;

@Category(ConfigMagicTests.class)
public class TestCoercion {

    private ConfigurationObjectFactory c = null;

    @Before
    public void setUp() {
        this.c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("the-url", "http://github.org/brianm/config-magic");
        }});
    }

    @After
    public void tearDown() {
        this.c = null;
    }

    @Test(expected = IllegalStateException.class)
    public void testBadConfig() {
        c.build(WibbleConfig.class);
    }

    @Test
    public void testGoodConfig() {
        final CoercionConfig cc = c.build(CoercionConfig.class);
        Assert.assertThat(cc.getURI(), is(URI.create("http://github.org/brianm/config-magic")));
    }

    @Test
    public void testEmptyURI() {
        final EmptyUriConfig euc1 = new EmptyUriConfig() {};
        Assert.assertNull(euc1.getTheUri());

        final EmptyUriConfig euc2 = c.build(EmptyUriConfig.class);
        Assert.assertNull(euc2.getTheUri());
    }

    @Test
    public void testNullDouble() {
        final NullDoubleConfig ndc1 = new NullDoubleConfig() {};
        Assert.assertNull(ndc1.getTheNumber());

        final NullDoubleConfig ndc2 = c.build(NullDoubleConfig.class);
        Assert.assertNull(ndc2.getTheNumber());
    }

    public abstract static class EmptyUriConfig {

        @Config("the-uri")
        @DefaultNull
        public URI getTheUri() {
            return null;
        }
    }

    public abstract static class NullDoubleConfig {

        @Config("the-number")
        @DefaultNull
        public Double getTheNumber() {
            return null;
        }
    }
}
