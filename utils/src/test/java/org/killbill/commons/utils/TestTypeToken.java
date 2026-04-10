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
import java.util.LinkedHashSet;
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
        final Set<Class<?>> integerExpected = new LinkedHashSet<>();
        integerExpected.add(Integer.class);
        integerExpected.add(Comparable.class);
        addIfPresent(integerExpected, "java.lang.constant.Constable");
        addIfPresent(integerExpected, "java.lang.constant.ConstantDesc");
        integerExpected.add(Number.class);
        integerExpected.add(java.io.Serializable.class);
        integerExpected.add(Object.class);
        Assert.assertEquals(types, integerExpected);

        // Guava version: com.google.common.reflect.TypeToken.of(SuperList.class).getTypes().rawTypes() . Size = 11.
        types = TypeToken.getRawTypes(SuperList.class);
        final Set<Class<?>> expected = new LinkedHashSet<>();
        expected.add(SuperList.class);
        expected.add(ArrayList.class);
        expected.add(List.class);
        addIfPresent(expected, "java.util.SequencedCollection");
        expected.add(Collection.class);
        expected.add(Iterable.class);
        expected.add(java.util.RandomAccess.class);
        expected.add(Cloneable.class);
        expected.add(java.io.Serializable.class);
        expected.add(java.util.AbstractList.class);
        expected.add(java.util.AbstractCollection.class);
        expected.add(Object.class);
        Assert.assertEquals(types, expected);

        types = TypeToken.getRawTypes(String.class);
        final Set<Class<?>> stringExpected = new LinkedHashSet<>();
        stringExpected.add(String.class);
        stringExpected.add(java.io.Serializable.class);
        stringExpected.add(Comparable.class);
        stringExpected.add(CharSequence.class);
        addIfPresent(stringExpected, "java.lang.constant.Constable");
        addIfPresent(stringExpected, "java.lang.constant.ConstantDesc");
        stringExpected.add(Object.class);
        Assert.assertEquals(types, stringExpected);
    }

    // Newer JDKs add interfaces such as SequencedCollection/Constable/ConstantDesc, so the test
    // builds the exact expected set while remaining compatible with earlier runtimes that lack them.
    private void addIfPresent(final Set<Class<?>> types, final String className) {
        try {
            types.add(Class.forName(className));
        } catch (final ClassNotFoundException ignored) {
        }
    }
}
