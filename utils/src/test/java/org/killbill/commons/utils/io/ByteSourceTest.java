/*
 * Copyright 2014-2026 The Billing Project, LLC
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

package org.killbill.commons.utils.io;

import java.io.IOException;
import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ByteSourceTest {

    @Test
    public void testWrap() throws IOException {
        byte[] data = {1, 2, 3, 4, 5};
        ByteSource source = ByteSource.wrap(data);
        Assert.assertEquals(source.read(), data);
    }

    @Test
    public void testWrap_returnsClone() throws IOException {
        byte[] data = {10, 20, 30};
        ByteSource source = ByteSource.wrap(data);
        byte[] first = source.read();
        byte[] second = source.read();
        Assert.assertEquals(first, second);
        // modifying returned array should not affect future reads
        first[0] = 99;
        Assert.assertEquals(source.read(), new byte[]{10, 20, 30});
    }

    @Test
    public void testWrap_emptyArray() throws IOException {
        ByteSource source = ByteSource.wrap(new byte[0]);
        Assert.assertEquals(source.read(), new byte[0]);
    }

    @Test
    public void testWrap_toString() {
        ByteSource source = ByteSource.wrap(new byte[]{1, 2, 3});
        Assert.assertTrue(source.toString().contains("3 bytes"));
    }

    @Test
    public void testEmpty() throws IOException {
        ByteSource empty = ByteSource.empty();
        Assert.assertEquals(empty.read(), new byte[0]);
    }

    @Test
    public void testEmpty_sameInstance() {
        Assert.assertSame(ByteSource.empty(), ByteSource.empty());
    }

    @Test
    public void testEmpty_toString() {
        Assert.assertEquals(ByteSource.empty().toString(), "ByteSource.empty()");
    }

    @Test
    public void testConcat_multipleSources() throws IOException {
        ByteSource a = ByteSource.wrap(new byte[]{1, 2});
        ByteSource b = ByteSource.wrap(new byte[]{3, 4});
        ByteSource c = ByteSource.wrap(new byte[]{5});
        ByteSource concat = ByteSource.concat(a, b, c);
        Assert.assertEquals(concat.read(), new byte[]{1, 2, 3, 4, 5});
    }

    @Test
    public void testConcat_singleSource() throws IOException {
        ByteSource a = ByteSource.wrap(new byte[]{7, 8, 9});
        ByteSource concat = ByteSource.concat(a);
        Assert.assertEquals(concat.read(), new byte[]{7, 8, 9});
    }

    @Test
    public void testConcat_noSources() throws IOException {
        ByteSource concat = ByteSource.concat();
        Assert.assertEquals(concat.read(), new byte[0]);
    }

    @Test
    public void testConcat_withEmpty() throws IOException {
        ByteSource a = ByteSource.wrap(new byte[]{1, 2});
        ByteSource concat = ByteSource.concat(ByteSource.empty(), a, ByteSource.empty());
        Assert.assertEquals(concat.read(), new byte[]{1, 2});
    }

    @Test
    public void testConcat_toString() {
        ByteSource concat = ByteSource.concat(
                ByteSource.wrap(new byte[1]),
                ByteSource.wrap(new byte[2]));
        Assert.assertTrue(concat.toString().contains("2 sources"));
    }

    @Test
    public void testSlice_middle() throws IOException {
        byte[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        ByteSource source = ByteSource.wrap(data);
        ByteSource slice = source.slice(3, 4);
        Assert.assertEquals(slice.read(), new byte[]{3, 4, 5, 6});
    }

    @Test
    public void testSlice_fromStart() throws IOException {
        byte[] data = {10, 20, 30, 40, 50};
        ByteSource source = ByteSource.wrap(data);
        ByteSource slice = source.slice(0, 3);
        Assert.assertEquals(slice.read(), new byte[]{10, 20, 30});
    }

    @Test
    public void testSlice_toEnd() throws IOException {
        byte[] data = {1, 2, 3, 4, 5};
        ByteSource source = ByteSource.wrap(data);
        ByteSource slice = source.slice(2, 100);
        Assert.assertEquals(slice.read(), new byte[]{3, 4, 5});
    }

    @Test
    public void testSlice_offsetBeyondEnd() throws IOException {
        byte[] data = {1, 2, 3};
        ByteSource source = ByteSource.wrap(data);
        ByteSource slice = source.slice(10, 5);
        Assert.assertEquals(slice.read(), new byte[0]);
    }

    @Test
    public void testSlice_zeroLength() throws IOException {
        byte[] data = {1, 2, 3};
        ByteSource source = ByteSource.wrap(data);
        ByteSource slice = source.slice(1, 0);
        Assert.assertEquals(slice.read(), new byte[0]);
    }

    @Test
    public void testSlice_ofSlice() throws IOException {
        byte[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        ByteSource source = ByteSource.wrap(data);
        ByteSource slice = source.slice(2, 6).slice(1, 3);
        Assert.assertEquals(slice.read(), new byte[]{3, 4, 5});
    }

    @Test
    public void testSlice_toString() {
        ByteSource source = ByteSource.wrap(new byte[10]);
        ByteSource slice = source.slice(2, 5);
        String str = slice.toString();
        Assert.assertTrue(str.contains("slice"));
        Assert.assertTrue(str.contains("2"));
        Assert.assertTrue(str.contains("5"));
    }

    @Test
    public void testConcat_thenSlice() throws IOException {
        ByteSource a = ByteSource.wrap(new byte[]{1, 2, 3});
        ByteSource b = ByteSource.wrap(new byte[]{4, 5, 6});
        ByteSource concat = ByteSource.concat(a, b);
        ByteSource slice = concat.slice(2, 3);
        Assert.assertEquals(slice.read(), new byte[]{3, 4, 5});
    }

    @Test
    public void testConcat_nestedConcat() throws IOException {
        ByteSource a = ByteSource.wrap(new byte[]{1});
        ByteSource b = ByteSource.wrap(new byte[]{2});
        ByteSource c = ByteSource.wrap(new byte[]{3});
        ByteSource nested = ByteSource.concat(ByteSource.concat(a, b), c);
        Assert.assertEquals(nested.read(), new byte[]{1, 2, 3});
    }

    @Test
    public void testSlice_ofEmpty() throws IOException {
        ByteSource slice = ByteSource.empty().slice(0, 10);
        Assert.assertEquals(slice.read(), new byte[0]);
    }

    @Test
    public void testWrap_largeArray() throws IOException {
        byte[] data = new byte[10000];
        Arrays.fill(data, (byte) 42);
        ByteSource source = ByteSource.wrap(data);
        Assert.assertEquals(source.read().length, 10000);
        Assert.assertEquals(source.read()[9999], (byte) 42);
    }
}
