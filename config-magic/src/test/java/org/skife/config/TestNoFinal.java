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

import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(ConfigMagicTests.class)
public class TestNoFinal {

    @Test(expected = IllegalArgumentException.class)
    public void testExplodeOnFinal() {
        final ConfigurationObjectFactory cof = new AugmentedConfigurationObjectFactory(new Properties());
        cof.build(EmptyClass.class);
    }

    public static final class EmptyClass {

        @Config("value")
        public String getValue() {
            return "default-value";
        }
    }
}

