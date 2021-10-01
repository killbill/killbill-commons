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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(ConfigMagicTests.class)
public class TestDefaultNull {

    private ConfigurationObjectFactory cof = null;

    @Before
    public void setUp() {
        cof = new ConfigurationObjectFactory(new Properties());
    }

    @After
    public void tearDown() {
        cof = null;
    }

    @Test
    public void testClass() {
        final EmptyClass ec = cof.build(EmptyClass.class);

        Assert.assertNull(ec.getValue());
    }

    @Test
    public void testInterface() {
        final EmptyInterface ec = cof.build(EmptyInterface.class);

        Assert.assertNull(ec.getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDoubleFeature() {
        cof.build(DoubleFeature.class);
    }

    public interface EmptyInterface {

        @Config("value")
        @DefaultNull
        String getValue();
    }

    public abstract static class EmptyClass {

        @Config("value")
        @DefaultNull
        public abstract String getValue();
    }

    public static class DoubleFeature {

        @Config("value")
        @DefaultNull
        @Default("value-default")
        public String getValue() {
            return "default-value";
        }
    }
}

