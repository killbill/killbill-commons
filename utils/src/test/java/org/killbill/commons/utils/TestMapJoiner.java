/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.commons.utils;

import java.util.Map;
import java.util.TreeMap;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestMapJoiner {

    private static final String SEPARATOR = "=";
    private static final String KV_SEPARATOR = "&";

    private MapJoiner mapJoiner;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        mapJoiner = new MapJoiner(SEPARATOR, KV_SEPARATOR);
    }

    @Test(groups = "fast")
    public void testJoin() {
        final Map<String, String> map = Map.of("usa", "washington",
                                               "russia", "moscow",
                                               "france", "paris",
                                               "indonesia", "jakarta");

        Assert.assertEquals(mapJoiner.join(new TreeMap<>(map)), "france=paris&indonesia=jakarta&russia=moscow&usa=washington");
    }

    @Test(groups = "fast")
    public void testJoinWithEmptyEntry() {
        Map<Object, Object> map = Map.of("", "washington",
                                               "russia", "moscow",
                                               "france", "",
                                               "indonesia", "jakarta");

        Assert.assertEquals(mapJoiner.join(new TreeMap<>(map)), "france=&indonesia=jakarta&russia=moscow");

        map = Map.of("", false,
                     "france", "",
                     "indonesia", "jakarta",
                     "usa", "");

        Assert.assertEquals(mapJoiner.join(new TreeMap<>(map)), "france=&indonesia=jakarta&usa=");

        map = Map.of("germany", "berlin",
                     "france", "",
                     "indonesia", "");

        Assert.assertEquals(mapJoiner.join(new TreeMap<>(map)), "france=&germany=berlin&indonesia=");
    }
}
