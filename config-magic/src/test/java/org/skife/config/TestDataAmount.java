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
public class TestDataAmount {

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
    public void testKiloBytes() {
        DataAmount amount = new DataAmount("20kB");
        Assert.assertEquals(DataAmountUnit.KILOBYTE, amount.getUnit());
        Assert.assertEquals(20L * 1000, amount.getNumberOfBytes());
        // and space allowed now as well
        amount = new DataAmount("20 kB");
        Assert.assertEquals(DataAmountUnit.KILOBYTE, amount.getUnit());
        Assert.assertEquals(20L * 1000, amount.getNumberOfBytes());

        final ClassWithKilobytes ec = cof.build(ClassWithKilobytes.class);

        Assert.assertEquals(DataAmountUnit.KILOBYTE, ec.getValue().getUnit());
        Assert.assertEquals(10L * 1000, ec.getValue().getNumberOfBytes());
    }

    @Test
    public void testRawBytes() {
        DataAmount amt = new DataAmount("1024");
        Assert.assertEquals(DataAmountUnit.BYTE, amt.getUnit());
        Assert.assertEquals(1024L, amt.getNumberOfBytes());

        amt = new DataAmount(2000);
        Assert.assertEquals(DataAmountUnit.BYTE, amt.getUnit());
        Assert.assertEquals(2000L, amt.getNumberOfBytes());
    }

    public abstract static class ClassWithKilobytes {

        @Config("value")
        @Default("10kB")
        public abstract DataAmount getValue();
    }
}
