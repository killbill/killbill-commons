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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 *
 */
@Category(ConfigMagicTests.class)
public class TestConfigurationObjectFactory {

    @Test
    public void testMultipleReplacements() throws Exception {
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("another-option.a.1", "A1");
            setProperty("another-option.a.2", "A2");
            setProperty("another-option.b.1", "B1");
            setProperty("another-option.b.2", "B2");
        }});

        ReplacementConfig1 r;
        final Map<String, String> replacementsMap = new HashMap<String, String>();

        replacementsMap.put("type", "a");
        replacementsMap.put("s", "1");
        r = c.buildWithReplacements(ReplacementConfig1.class, replacementsMap);
        assertEquals(r.getStringOption2Types(), "A1");

        replacementsMap.put("type", "a");
        replacementsMap.put("s", "2");
        r = c.buildWithReplacements(ReplacementConfig1.class, replacementsMap);
        assertEquals(r.getStringOption2Types(), "A2");

        replacementsMap.put("type", "b");
        replacementsMap.put("s", "1");
        r = c.buildWithReplacements(ReplacementConfig1.class, replacementsMap);
        assertEquals(r.getStringOption2Types(), "B1");

        replacementsMap.put("type", "b");
        replacementsMap.put("s", "2");
        r = c.buildWithReplacements(ReplacementConfig1.class, replacementsMap);
        assertEquals(r.getStringOption2Types(), "B2");
    }

    @Test
    public void testReplacement() throws Exception {
        final Map<String, String> replacementsMap = new HashMap<String, String>();
        replacementsMap.put("type", "first");
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("option.first", "1st");
            setProperty("option.second", "2nd");
        }});
        ReplacementConfig1 r = c.buildWithReplacements(ReplacementConfig1.class, replacementsMap);
        assertEquals(r.getStringOption(), "1st");

        replacementsMap.put("type", "second");
        r = c.buildWithReplacements(ReplacementConfig1.class, replacementsMap);
        assertEquals(r.getStringOption(), "2nd");
    }

    @Test
    public void testFoo() throws Exception {
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("hello", "world");
            setProperty("theValue", "value");
        }});
        final Thing t = c.build(Thing.class);
        assertEquals(t.getName(), "world");
    }

    @Test
    public void testSameConfigObjectClassUsedForEachInstance() throws Exception {
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("hello", "world");
            setProperty("theValue", "value");
        }});
        final Thing t = c.build(Thing.class);
        final Thing t2 = c.build(Thing.class);

        assertEquals(t.getClass(), t2.getClass());
    }

    @Test
    public void testSameConfigObjectClassUsedForEachInstanceEvenWithDifferentCOFs() throws Exception {
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("hello", "world");
            setProperty("theValue", "value");
        }});

        final ConfigurationObjectFactory c2 = new ConfigurationObjectFactory(new Properties() {{
            setProperty("hello", "world");
            setProperty("theValue", "value");
        }});

        final Thing t = c.build(Thing.class);
        final Thing t2 = c2.build(Thing.class);

        assertEquals(t.getClass(), t2.getClass());
    }

    @Test
    public void testSameConfigObjectClassUsedForEachInstance2() throws Exception {

        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("t1.hello", "world");
            setProperty("t1.value", "value");
            setProperty("t2.hello", "brian");
            setProperty("t2.value", "another-value");
        }});
        final ThingParam t1 = c.buildWithReplacements(ThingParam.class, Collections.singletonMap("thing", "t1"));
        final ThingParam t2 = c.buildWithReplacements(ThingParam.class, Collections.singletonMap("thing", "t2"));

        assertEquals(t1.getClass(), t2.getClass());

        assertEquals("world", t1.getHello());
        assertEquals("value", t1.getValue());

        assertEquals("brian", t2.getHello());
        assertEquals("another-value", t2.getValue());
    }

    @Test
    public void testEnum() throws Exception {
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("option.one", "1");
            setProperty("option.two", "2");
        }});
        final EnumeratedConfig1 t = c.build(EnumeratedConfig1.class);
        assertEquals(t.getStringOption(ConfigEnum.ONE), "1");
        assertEquals(t.getStringOption(ConfigEnum.TWO), "2");
        assertEquals(t.getStringOption(ConfigEnum.THREE), "default");
    }

    @Test
    public void testMultiParameters() throws Exception {
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("another-option.one.a", "1-x");
            setProperty("another-option.two.b", "2-y");
        }});
        final EnumeratedConfig1 t = c.build(EnumeratedConfig1.class);
        assertEquals(t.getStringOption2Types(ConfigEnum.ONE, "a"), "1-x");
        assertEquals(t.getStringOption2Types(ConfigEnum.TWO, "b"), "2-y");
        assertEquals(t.getStringOption2Types(ConfigEnum.ONE, "dummy"), "default");
    }

    @Test
    public void testDefaultValue() throws Exception {
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties());
        final Thing t = c.build(Thing.class);
        assertEquals(t.getName(), "woof");
    }

    @Test
    public void testDefaultViaImpl() throws Exception {
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties());
        final Config2 config = c.build(Config2.class);
        assertEquals(config.getOption(), "default");
    }

    @Test
    public void testProvidedOverridesDefault() throws Exception {
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("option", "provided");
        }});

        final Config2 config = c.build(Config2.class);
        assertEquals(config.getOption(), "provided");
    }

    @Test
    public void testMissingDefault() throws Exception {
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties());
        try {
            c.build(Config3.class);
            fail("Expected exception due to missing value");
        } catch (final Throwable e) {
        }
    }

    @Test
    public void testDetectsAbstractMethod() throws Exception {
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties());
        try {
            c.build(Config4.class);
            fail("Expected exception due to abstract method without @Config annotation");
        } catch (final AbstractMethodError e) {
        }
    }

    @Test
    public void testTypes() {
        final ConfigurationObjectFactory c = new ConfigurationObjectFactory(new Properties() {{
            setProperty("stringOption", "a string");
            setProperty("booleanOption", "true");
            setProperty("boxedBooleanOption", "true");
            setProperty("byteOption", Byte.toString(Byte.MAX_VALUE));
            setProperty("boxedByteOption", Byte.toString(Byte.MAX_VALUE));
            setProperty("shortOption", Short.toString(Short.MAX_VALUE));
            setProperty("boxedShortOption", Short.toString(Short.MAX_VALUE));
            setProperty("integerOption", Integer.toString(Integer.MAX_VALUE));
            setProperty("boxedIntegerOption", Integer.toString(Integer.MAX_VALUE));
            setProperty("longOption", Long.toString(Long.MAX_VALUE));
            setProperty("boxedLongOption", Long.toString(Long.MAX_VALUE));
            setProperty("floatOption", Float.toString(Float.MAX_VALUE));
            setProperty("boxedFloatOption", Float.toString(Float.MAX_VALUE));
            setProperty("doubleOption", Double.toString(Double.MAX_VALUE));
            setProperty("boxedDoubleOption", Double.toString(Double.MAX_VALUE));
        }});

        final Config1 config = c.build(Config1.class);
        assertEquals("a string", config.getStringOption());
        assertEquals(true, config.getBooleanOption());
        assertEquals(Boolean.TRUE, config.getBoxedBooleanOption());
        assertEquals(Byte.MAX_VALUE, config.getByteOption());
        assertEquals(Byte.valueOf(Byte.MAX_VALUE), config.getBoxedByteOption());
        assertEquals(Short.MAX_VALUE, config.getShortOption());
        assertEquals(Short.valueOf(Short.MAX_VALUE), config.getBoxedShortOption());
        assertEquals(Integer.MAX_VALUE, config.getIntegerOption());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), config.getBoxedIntegerOption());
        assertEquals(Long.MAX_VALUE, config.getLongOption());
        assertEquals(Long.valueOf(Long.MAX_VALUE), config.getBoxedLongOption());
        assertEquals(Float.MAX_VALUE, config.getFloatOption(), 0);
        assertEquals(Float.valueOf(Float.MAX_VALUE), config.getBoxedFloatOption());
        assertEquals(Double.MAX_VALUE, config.getDoubleOption(), 0);
        assertEquals(Double.valueOf(Double.MAX_VALUE), config.getBoxedDoubleOption());
    }

    private interface ThingParam {

        @Config("${thing}.hello")
        String getHello();

        @Config("${thing}.value")
        String getValue();
    }

    public abstract static class Thing {

        @Config("hello")
        @Default("woof")
        public abstract String getName();
    }
}
