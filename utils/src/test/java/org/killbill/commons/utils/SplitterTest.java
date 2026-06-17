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

package org.killbill.commons.utils;

import java.util.Iterator;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

public class SplitterTest {

    @Test
    public void testOnChar_basic() {
        List<String> result = Splitter.on(',').splitToList("a,b,c");
        Assert.assertEquals(result, List.of("a", "b", "c"));
    }

    @Test
    public void testOnChar_singleElement() {
        List<String> result = Splitter.on(',').splitToList("hello");
        Assert.assertEquals(result, List.of("hello"));
    }

    @Test
    public void testOnChar_emptyParts() {
        List<String> result = Splitter.on(',').splitToList("a,,b,");
        Assert.assertEquals(result, List.of("a", "", "b", ""));
    }

    @Test
    public void testOnChar_emptyInput() {
        List<String> result = Splitter.on(',').splitToList("");
        Assert.assertEquals(result, List.of(""));
    }

    @Test
    public void testTrimResults() {
        List<String> result = Splitter.on(',').trimResults().splitToList(" a , b , c ");
        Assert.assertEquals(result, List.of("a", "b", "c"));
    }

    @Test
    public void testOmitEmptyStrings() {
        List<String> result = Splitter.on(',').omitEmptyStrings().splitToList("a,,b,,c");
        Assert.assertEquals(result, List.of("a", "b", "c"));
    }

    @Test
    public void testTrimResults_andOmitEmpty() {
        List<String> result = Splitter.on(',').trimResults().omitEmptyStrings()
                .splitToList(" a , , b , , ");
        Assert.assertEquals(result, List.of("a", "b"));
    }

    @Test
    public void testOmitEmpty_andTrimResults_orderDoesNotMatter() {
        List<String> result = Splitter.on(',').omitEmptyStrings().trimResults()
                .splitToList(" a , , b , , ");
        Assert.assertEquals(result, List.of("a", "b"));
    }

    @Test
    public void testOnCharMatcher_anyOf() {
        List<String> result = Splitter.on(CharMatcher.anyOf("[].")).trimResults()
                .omitEmptyStrings().splitToList("foo[0].bar");
        Assert.assertEquals(result, List.of("foo", "0", "bar"));
    }

    @Test
    public void testOnCharMatcher_anyOf_multipleDelimiters() {
        List<String> result = Splitter.on(CharMatcher.anyOf("&=")).splitToList("a=1&b=2");
        Assert.assertEquals(result, List.of("a", "1", "b", "2"));
    }

    @Test
    public void testSplit_returnsIterable() {
        Iterable<String> result = Splitter.on('&').trimResults().omitEmptyStrings()
                .split("a & b & c");
        Iterator<String> it = result.iterator();
        Assert.assertTrue(it.hasNext());
        Assert.assertEquals(it.next(), "a");
        Assert.assertEquals(it.next(), "b");
        Assert.assertEquals(it.next(), "c");
        Assert.assertFalse(it.hasNext());
    }

    @Test
    public void testSplitToList_unmodifiable() {
        List<String> result = Splitter.on(',').splitToList("a,b");
        try {
            result.add("c");
            Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    public void testOnNewline() {
        List<String> result = Splitter.on('\n').splitToList("line1\nline2\nline3");
        Assert.assertEquals(result, List.of("line1", "line2", "line3"));
    }

    @Test
    public void testOnEquals() {
        List<String> result = Splitter.on('=').trimResults().omitEmptyStrings()
                .split("key=value").iterator().next().isEmpty() ? List.of() : null;
        // More direct test:
        Iterator<String> it = Splitter.on('=').trimResults().omitEmptyStrings()
                .split("key=value").iterator();
        Assert.assertEquals(it.next(), "key");
        Assert.assertEquals(it.next(), "value");
        Assert.assertFalse(it.hasNext());
    }

    @Test
    public void testOnAmpersand_cookiePattern() {
        // Matches Cookie.java usage: Splitter.on('&').trimResults().omitEmptyStrings()
        List<String> result = Splitter.on('&').trimResults().omitEmptyStrings()
                .splitToList("name=val&other=x&");
        Assert.assertEquals(result, List.of("name=val", "other=x"));
    }

    @Test
    public void testOnComma_corsPattern() {
        // Matches CorsHandler.java: Splitter.on(',').trimResults().omitEmptyStrings()
        List<String> result = Splitter.on(',').trimResults().omitEmptyStrings()
                .splitToList("GET, POST, PUT");
        Assert.assertEquals(result, List.of("GET", "POST", "PUT"));
    }

    @Test
    public void testImmutability_trimDoesNotModifyOriginal() {
        Splitter base = Splitter.on(',');
        Splitter trimmed = base.trimResults();
        // base should still not trim
        Assert.assertEquals(base.splitToList(" a , b "), List.of(" a ", " b "));
        Assert.assertEquals(trimmed.splitToList(" a , b "), List.of("a", "b"));
    }

    @Test
    public void testImmutability_omitDoesNotModifyOriginal() {
        Splitter base = Splitter.on(',');
        Splitter omitting = base.omitEmptyStrings();
        Assert.assertEquals(base.splitToList("a,,b"), List.of("a", "", "b"));
        Assert.assertEquals(omitting.splitToList("a,,b"), List.of("a", "b"));
    }
}
