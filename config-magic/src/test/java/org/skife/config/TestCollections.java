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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(ConfigMagicTests.class)
public class TestCollections
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
    public void testClassWithListDefault()
    {
        EmptyClassList ec = cof.build(EmptyClassList.class);

        Assert.assertEquals(Arrays.asList("one", "three", "two"), ec.getValue());
    }

    @Test
    public void testClassWithCollectionDefault()
    {
        EmptyClassCollection ec = cof.build(EmptyClassCollection.class);

        Assert.assertEquals(Arrays.asList("one", "three", "two"), ec.getValue());
    }

    @Test
    public void testAbstractClassDefault()
    {
    	EmptyAbstractClass ec = cof.build(EmptyAbstractClass.class);

        Assert.assertEquals(new HashSet<TestEnum>(Arrays.asList(TestEnum.TWO, TestEnum.ONE)), ec.getValue());
    }

    @Test
    public void testInterface()
    {
        EmptyInterface ec = cof.build(EmptyInterface.class);

        Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList("one", "two")), ec.getValue());
    }

    @Test
    public void testClassDefaultNull()
    {
    	EmptyClassDefaultNull ec = cof.build(EmptyClassDefaultNull.class);

        Assert.assertNull(ec.getValue());
    }

    @Test
    public void testAbstractClassDefaultNull()
    {
    	EmptyAbstractClassDefaultNull ec = cof.build(EmptyAbstractClassDefaultNull.class);

    	Assert.assertNull(ec.getValue());
    }

    @Test
    public void testInterfaceDefaultNull()
    {
        EmptyInterfaceDefaultNull ec = cof.build(EmptyInterfaceDefaultNull.class);

        Assert.assertNull(ec.getValue());
    }

    @Test
    public void testInterfaceDefaultEmptyString()
    {
        EmptyInterfaceEmptyString ec = cof.build(EmptyInterfaceEmptyString.class);

        Assert.assertEquals(Collections.emptyList(), ec.getValue());
    }

    @Test
    public void testDifferentSeparator()
    {
        DifferentSeparator ec = cof.build(DifferentSeparator.class);

        Assert.assertEquals(new HashSet<TestEnum>(Arrays.asList(TestEnum.TWO, TestEnum.ONE)), ec.getValue());
    }

    public static enum TestEnum
    {
        ONE,
        TWO,
        THREE
    }

    public static class EmptyClassList
    {
        @Config("value")
        @Default("one, three, two")
        public List<String> getValue()
        {
            return Collections.emptyList();
        }
    }

    public static class EmptyClassCollection
    {
        @Config("value")
        @Default("one, three, two")
        public Collection<String> getValue()
        {
            return Collections.emptyList();
        }
    }

    public static abstract class EmptyAbstractClass
    {
        @Config("value")
        @Default("TWO, ONE")
        public abstract Set<TestEnum> getValue();
    }

    public static interface EmptyInterface
    {
        @Config("value")
        @Default("one, two")
        public LinkedHashSet<String> getValue();
    }

    public static class EmptyClassDefaultNull
    {
        @Config("value")
        @DefaultNull
        public List<Float> getValue()
        {
            return null;
        }
    }

    public static abstract class EmptyAbstractClassDefaultNull
    {
        @Config("value")
        @DefaultNull
        public Set<String> getValue()
        {
            return null;
        }
    }

    public static interface EmptyInterfaceDefaultNull
    {
        @Config("value")
        @DefaultNull
        public List<TestEnum> getValue();
    }

    public static interface EmptyInterfaceEmptyString
    {
        @Config("value")
        @Default("")
        public List<TestEnum> getValue();
    }


    public static interface DifferentSeparator
    {
        @Config("value")
        @Separator("\\s*!\\s*")
        @Default("TWO ! ONE")
        public Set<TestEnum> getValue();
    }
}
