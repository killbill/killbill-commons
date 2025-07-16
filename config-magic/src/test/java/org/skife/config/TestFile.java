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

import java.io.File;
import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(ConfigMagicTests.class)
public class TestFile {

    private AugmentedConfigurationObjectFactory cof;

    @Before
    public void setUp() {
        cof = new AugmentedConfigurationObjectFactory(new Properties() {{
            setProperty("file2", "..");
        }});
    }

    @After
    public void tearDown() {
        cof = null;
    }

    @Test
    public void testClassDefault() {
        final EmptyClass ec = cof.build(EmptyClass.class);

        Assert.assertEquals(new File("."), ec.getFile());
    }

    @Test
    public void testAbstractClassDefault() {
        final EmptyAbstractClass ec = cof.build(EmptyAbstractClass.class);

        Assert.assertEquals(new File(".."), ec.getFile());
    }

    @Test
    public void testAbstractClassDefaultNull() {
        final EmptyAbstractClassDefaultNull ec = cof.build(EmptyAbstractClassDefaultNull.class);

        Assert.assertNull(ec.getFile());
    }

    public static class EmptyClass {

        @Config("file1")
        @Default(".")
        public File getFile() {
            return null;
        }
    }

    public abstract static class EmptyAbstractClass {

        @Config("file2")
        public abstract File getFile();
    }

    public abstract static class EmptyAbstractClassDefaultNull {

        @Config("file3")
        @DefaultNull
        public abstract File getFile();
    }
}
