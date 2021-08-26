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
public class TestArrays {

    private ConfigurationObjectFactory cof;

    @Before
    public void setUp() {
        cof = new ConfigurationObjectFactory(new Properties());
    }

    @After
    public void tearDown() {
        cof = null;
    }

    @Test
    public void testClassDefault() {
        final EmptyClass ec = cof.build(EmptyClass.class);

        Assert.assertArrayEquals(new String[]{"one", "three", "two"}, ec.getValue());
    }

    @Test
    public void testAbstractClassDefault() {
        final EmptyAbstractClass ec = cof.build(EmptyAbstractClass.class);

        Assert.assertArrayEquals(new TestEnum[]{TestEnum.TWO, TestEnum.ONE}, ec.getValue());
    }

    @Test
    public void testInterface() {
        final EmptyInterface ec = cof.build(EmptyInterface.class);

        Assert.assertArrayEquals(new float[]{1.0f, 2.0f}, ec.getValue(), 0.0f);
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

    @Test
    public void testInterfaceDefaultEmptyString() {
        final EmptyInterfaceEmptyString ec = cof.build(EmptyInterfaceEmptyString.class);

        Assert.assertArrayEquals(new int[0], ec.getValue());
    }

    @Test
    public void testDifferentSeparator() {
        final DifferentSeparator ec = cof.build(DifferentSeparator.class);

        Assert.assertArrayEquals(new float[]{1.0f, 2.0f}, ec.getValue(), 0.0f);
    }

    public enum TestEnum {
        ONE,
        TWO,
        THREE
    }

    public interface EmptyInterface {

        @Config("value")
        @Default("1.0, 2.0")
        float[] getValue();
    }

    public interface EmptyInterfaceDefaultNull {

        @Config("value")
        @DefaultNull
        TestEnum[] getValue();
    }

    public interface EmptyInterfaceEmptyString {

        @Config("value")
        @Default("")
        int[] getValue();
    }

    public interface DifferentSeparator {

        @Config("value")
        @Separator("\\s*;\\s*")
        @Default("1.0 ; 2.0")
        float[] getValue();
    }

    public static class EmptyClass {

        @Config("value")
        @Default("one, three, two")
        public String[] getValue() {
            return null;
        }
    }

    public abstract static class EmptyAbstractClass {

        @Config("value")
        @Default("TWO, ONE")
        public abstract TestEnum[] getValue();
    }

    public static class EmptyClassDefaultNull {

        @Config("value")
        @DefaultNull
        public int[] getValue() {
            return null;
        }
    }

    public abstract static class EmptyAbstractClassDefaultNull {

        @Config("value")
        @DefaultNull
        public String[] getValue() {
            return null;
        }
    }
}
