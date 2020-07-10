/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.commons.profiling;


import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestProfilingFeature {

    @Test(groups = "fast")
    public void testSimpleFeature() {
        final ProfilingFeature f = new ProfilingFeature("API");
        assertTrue(f.isProfilingAPI());
        assertFalse(f.isProfilingDAO());
        assertFalse(f.isProfilingDAOWithDetails());
        assertFalse(f.isProfilingPlugin());
    }

    @Test(groups = "fast")
    public void testTwoFeatures() {
        final ProfilingFeature f = new ProfilingFeature("API,DAO");
        assertTrue(f.isProfilingAPI());
        assertTrue(f.isProfilingDAO());
        assertFalse(f.isProfilingDAOWithDetails());
        assertFalse(f.isProfilingPlugin());
    }

    @Test(groups = "fast")
    public void testThreeFeatures() {
        final ProfilingFeature f = new ProfilingFeature(" API, DAO, PLUGIN");
        assertTrue(f.isProfilingAPI());
        assertTrue(f.isProfilingDAO());
        assertFalse(f.isProfilingDAOWithDetails());
        assertTrue(f.isProfilingPlugin());
    }

    @Test(groups = "fast")
    public void testThreeFeatures2() {
        final ProfilingFeature f = new ProfilingFeature(" API, DAO_DETAILS, PLUGIN");
        assertTrue(f.isProfilingAPI());
        assertTrue(f.isProfilingDAO());
        assertTrue(f.isProfilingDAOWithDetails());
        assertTrue(f.isProfilingPlugin());
    }

    @Test(groups = "fast")
    public void testTwoRealFeatures2() {
        final ProfilingFeature f = new ProfilingFeature(" API, FOO, PLUGIN");
        assertTrue(f.isProfilingAPI());
        assertFalse(f.isProfilingDAO());
        assertFalse(f.isProfilingDAOWithDetails());
        assertTrue(f.isProfilingPlugin());
    }
}
