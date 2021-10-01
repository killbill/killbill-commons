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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(ConfigMagicTests.class)
public class TestClasses {

    @Test
    public void testRawType() {
        final WithRawType config = new ConfigurationObjectFactory(Props.of("theClazz", Object.class.getName())).build(WithRawType.class);

        Assert.assertEquals(config.getTheClazz(), Object.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRawTypeNotFound() {
        new ConfigurationObjectFactory(Props.of("theClazz", "does.not.Exist")).build(WithRawType.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRawTypeIllegal() {
        new ConfigurationObjectFactory(Props.of("theClazz", "not a class")).build(WithRawType.class);
    }

    @Test
    public void testRawTypeWithDefault() {
        final WithRawTypeAndDefault config = new ConfigurationObjectFactory(new Properties()).build(WithRawTypeAndDefault.class);

        Assert.assertEquals(config.getTheClazz(), Object.class);
    }

    @Test
    public void testRawTypeWithNullDefault() {
        final WithRawType config = new ConfigurationObjectFactory(new Properties()).build(WithRawType.class);

        Assert.assertNull(config.getTheClazz());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRawTypeWithNotFoundDefault() {
        new ConfigurationObjectFactory(new Properties()).build(WithRawTypeAndUndefinedDefault.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRawTypeWithIllegalDefault() {
        new ConfigurationObjectFactory(new Properties()).build(WithRawTypeAndIllegalDefault.class);
    }

    @Test
    public void testUnspecifiedType() {
        final WithUnspecifiedType config = new ConfigurationObjectFactory(Props.of("theClazz", Foo.class.getName())).build(WithUnspecifiedType.class);

        Assert.assertEquals(config.getTheClazz(), Foo.class);
    }

    @Test
    public void testExtends() {
        final WithExtends config = new ConfigurationObjectFactory(Props.of("theClazz", Foo.class.getName())).build(WithExtends.class);

        Assert.assertEquals(config.getTheClazz(), Foo.class);
    }

    @Test
    public void testExtendsWithSubClass() {
        final WithExtends config = new ConfigurationObjectFactory(Props.of("theClazz", FooSub.class.getName())).build(WithExtends.class);

        Assert.assertEquals(config.getTheClazz(), FooSub.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtendsWithSuperClass() {
        new ConfigurationObjectFactory(Props.of("theClazz", FooSuper.class.getName())).build(WithExtends.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtendsWithUnrelatedClass() {
        new ConfigurationObjectFactory(Props.of("theClazz", Properties.class.getName())).build(WithExtends.class);
    }

    @Test
    public void testNestedExtends() {
        final WithNestedExtends config = new ConfigurationObjectFactory(Props.of("theClazz", FooList.class.getName())).build(WithNestedExtends.class);

        Assert.assertEquals(config.getTheClazz(), FooList.class);
    }

    @Test
    public void testNestedExtendsWithSubClass() {
        final WithNestedExtends config = new ConfigurationObjectFactory(Props.of("theClazz", FooSubList.class.getName())).build(WithNestedExtends.class);

        Assert.assertEquals(config.getTheClazz(), FooSubList.class);
    }

    @Test
    public void testNestedExtendsWithSuperClass() {
        final WithNestedExtends config = new ConfigurationObjectFactory(Props.of("theClazz", FooSuperList.class.getName())).build(WithNestedExtends.class);

        Assert.assertEquals(config.getTheClazz(), FooSuperList.class);
    }

    @Test
    public void testNestedExtendsWithUnrelatedClass() {
        final WithNestedExtends config = new ConfigurationObjectFactory(Props.of("theClazz", StringList.class.getName())).build(WithNestedExtends.class);

        Assert.assertEquals(config.getTheClazz(), StringList.class);
    }

    public interface WithRawType {

        @SuppressWarnings("rawtypes")
        @Config("theClazz")
        @DefaultNull
        Class getTheClazz();
    }

    public interface WithRawTypeAndDefault {

        @SuppressWarnings("rawtypes")
        @Config("theClazz")
        @Default("java.lang.Object")
        Class getTheClazz();
    }

    public interface WithRawTypeAndUndefinedDefault {

        @SuppressWarnings("rawtypes")
        @Config("theClazz")
        @Default("does.not.Exist")
        Class getTheClazz();
    }

    public interface WithRawTypeAndIllegalDefault {

        @SuppressWarnings("rawtypes")
        @Config("theClazz")
        @Default("not a class")
        Class getTheClazz();
    }

    public interface WithUnspecifiedType {

        @Config("theClazz")
        Class<?> getTheClazz();
    }

    public interface WithExtends {

        @Config("theClazz")
        Class<? extends Foo> getTheClazz();
    }

    public interface WithNestedExtends {

        @Config("theClazz")
        Class<? extends List<? extends Foo>> getTheClazz();
    }

    public static class FooSuper {}

    public static class Foo extends FooSuper {}

    public static class FooSub extends Foo {}

    @SuppressWarnings("serial")
    public static class FooSuperList extends ArrayList<FooSuper> {}

    @SuppressWarnings("serial")
    public static class FooList extends ArrayList<Foo> {}

    @SuppressWarnings("serial")
    public static class FooSubList extends ArrayList<FooSub> {}

    @SuppressWarnings("serial")
    public static class StringList extends ArrayList<String> {}
}
