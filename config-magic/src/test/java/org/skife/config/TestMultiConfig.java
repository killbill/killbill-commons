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
import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.hamcrest.CoreMatchers.is;

@Category(ConfigMagicTests.class)
public class TestMultiConfig {

    AugmentedConfigurationObjectFactory c = null;

    @Before
    public void setUp() {
        this.c = new AugmentedConfigurationObjectFactory(new Properties() {{
            setProperty("singleOption", "the-single-value");
            setProperty("multiOption1", "the-multi-option1-value");
            setProperty("multiOption2", "the-multi-option2-value");
            setProperty("fooExtOption", "the-fooExt-option-value");
            setProperty("barExtOption", "the-barExt-option-value");
            setProperty("defaultOption", "the-default-option-value");
        }});
    }

    @After
    public void tearDown() {
        this.c = null;
    }

    @Test
    public void testSimple() {
        final MultiConfig mc = c.build(MultiConfig.class);
        Assert.assertThat(mc.getSingleOption(), is("the-single-value"));
    }

    @Test
    public void testSimple2() {
        final MultiConfig mc = c.build(MultiConfig.class);
        Assert.assertThat(mc.getSingleOption2(), is("the-single-value"));
    }

    @Test
    public void testMultiOption1() {
        final MultiConfig mc = c.build(MultiConfig.class);
        Assert.assertThat(mc.getMultiOption1(), is("the-multi-option1-value"));
    }

    @Test
    public void testMultiOption2() {
        final MultiConfig mc = c.build(MultiConfig.class);
        Assert.assertThat(mc.getMultiOption2(), is("the-multi-option2-value"));
    }

    @Test
    public void testMultiDefault() {
        final MultiConfig mc = c.build(MultiConfig.class);
        Assert.assertThat(mc.getMultiDefault(), is("theDefault"));
    }

    @Test
    public void testMultiReplace1() {
        final MultiConfig mc = c.buildWithReplacements(MultiConfig.class, Collections.singletonMap("key", "foo"));
        Assert.assertThat(mc.getReplaceOption(), is("the-fooExt-option-value"));
    }

    @Test
    public void testMultiReplace2() {
        final MultiConfig mc = c.buildWithReplacements(MultiConfig.class, Collections.singletonMap("key", "bar"));
        Assert.assertThat(mc.getReplaceOption(), is("the-barExt-option-value"));
    }

    @Test
    public void testMultiReplaceDefault() {
        final MultiConfig mc = c.buildWithReplacements(MultiConfig.class, Collections.singletonMap("key", "baz"));
        Assert.assertThat(mc.getReplaceOption(), is("the-default-option-value"));
    }

}
