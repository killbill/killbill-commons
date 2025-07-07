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

import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@Category(ConfigMagicTests.class)
public class TestCaseInsensitiveEnumCoercible {

    @Test
    public void testHappyPath() throws Exception {
        final AugmentedConfigurationObjectFactory cof = new AugmentedConfigurationObjectFactory(Props.of("creamer", "half_and_half"));

        final Coffee coffee = cof.build(Coffee.class);
        assertThat(coffee.getCreamer(), equalTo(Creamer.HALF_AND_HALF));
    }

    @Test(expected = IllegalStateException.class)
    public void testNoMatch() throws Exception {
        final AugmentedConfigurationObjectFactory cof = new AugmentedConfigurationObjectFactory(Props.of("creamer", "goat_milk"));

        final Coffee coffee = cof.build(Coffee.class);
        fail("should have raised an illegal state exception");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExactMatch() throws Exception {
        final ConfigurationObjectFactory cof = new AugmentedConfigurationObjectFactory(Props.of("creamer", "whole_milk"));
        cof.addCoercible(new ExactMatchEnumCoercible());

        final Coffee coffee = cof.build(Coffee.class);
        fail("should have raised an exception");
    }

    public enum Creamer {
        HEAVY_CREAM, HALF_AND_HALF, WHOLE_MILK, SKIM_MILK, GROSS_WHITE_POWDER
    }

    public abstract static class Coffee {

        @Config("creamer")
        public abstract Creamer getCreamer();
    }
}
