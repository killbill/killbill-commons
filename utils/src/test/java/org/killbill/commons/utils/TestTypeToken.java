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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestTypeToken {

    private static class SuperList extends ArrayList<String> implements List<String>, Collection<String> {}

    @Test(groups = "fast")
    public void testGetRawTypes() {
        Set<Class<?>> types = TypeToken.getRawTypes(Object.class);
        Assert.assertEquals(types.size(), 1);

        types = TypeToken.getRawTypes(Integer.class);
        // class java.lang.Integer, interface java.lang.Comparable, class java.lang.Number, interface java.io.Serializable, class java.lang.Object
        // FIXME-1615 : JDK 11 and JDK 17 produce different result.
        // Assert.assertEquals(types.size(), 5);

        // Guava version: com.google.common.reflect.TypeToken.of(SuperList.class).getTypes().rawTypes() . Size = 11.
        types = TypeToken.getRawTypes(SuperList.class);
        Assert.assertEquals(types.size(), 11);

        types = TypeToken.getRawTypes(String.class);
        // class java.lang.String, interface java.lang.Comparable, interface java.io.Serializable, interface java.lang.CharSequence, class java.lang.Object
        // FIXME-1615 : JDK 11 and JDK 17 produce different result.
        // Assert.assertEquals(types.size(), 5);
    }
}
