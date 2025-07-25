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
public class TestEnums {

    private AugmentedConfigurationObjectFactory cof;

    @Before
    public void setUp() {
        cof = new AugmentedConfigurationObjectFactory(new Properties());
    }

    @After
    public void tearDown() {
        cof = null;
    }

    @Test
    public void testClassDefault() {
        final EmptyClass ec = cof.build(EmptyClass.class);

        Assert.assertEquals(TestEnum.ONE, ec.getValue());
    }

    @Test
    public void testAbstractClassDefault() {
        final EmptyAbstractClass ec = cof.build(EmptyAbstractClass.class);

        Assert.assertEquals(TestEnum.TWO, ec.getValue());
    }

    @Test
    public void testInterface() {
        final EmptyInterface ec = cof.build(EmptyInterface.class);

        Assert.assertEquals(TestEnum.THREE, ec.getValue());
    }

    @Test
    public void testClassDefaultNull() {
        final EmptyClassDefaultNull ec = cof.build(EmptyClassDefaultNull.class);

        Assert.assertNull(ec.getValue());
    }

    @Test
    public void testAbstractClassDefaultNull() {
        final EmptyAbstractClassDefaultNull ec = cof.build(EmptyAbstractClassDefaultNull.class);

        Assert.assertNull(ec.getValue());
    }

    @Test
    public void testInterfaceDefaultNull() {
        final EmptyInterfaceDefaultNull ec = cof.build(EmptyInterfaceDefaultNull.class);

        Assert.assertNull(ec.getValue());
    }

    public enum TestEnum {
        ONE,
        TWO,
        THREE
    }

    public interface EmptyInterface {

        @Config("value")
        @Default("THREE")
        TestEnum getValue();
    }

    public interface EmptyInterfaceDefaultNull {

        @Config("value")
        @DefaultNull
        TestEnum getValue();
    }

    public static class EmptyClass {

        @Config("value")
        @Default("ONE")
        public TestEnum getValue() {
            return TestEnum.ONE;
        }
    }

    public abstract static class EmptyAbstractClass {

        @Config("value")
        @Default("TWO")
        public abstract TestEnum getValue();
    }

    public static class EmptyClassDefaultNull {

        @Config("value")
        @DefaultNull
        public TestEnum getValue() {
            return TestEnum.THREE;
        }
    }

    public abstract static class EmptyAbstractClassDefaultNull {

        @Config("value")
        @DefaultNull
        public TestEnum getValue() {
            return null;
        }
    }
}
