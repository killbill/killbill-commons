/*
 * Copyright 2026 The Billing Project, LLC
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

package org.killbill.commons.utils.net;

import org.testng.Assert;
import org.testng.annotations.Test;

public class UrlEscapersTest {

    // --- urlFormParameterEscaper ---

    @Test
    public void testFormParam_space() {
        Assert.assertEquals(UrlEscapers.urlFormParameterEscaper().escape("hello world"), "hello+world");
    }

    @Test
    public void testFormParam_specialChars() {
        Assert.assertEquals(UrlEscapers.urlFormParameterEscaper().escape("a=b&c=d"), "a%3Db%26c%3Dd");
    }

    @Test
    public void testFormParam_unreserved() {
        Assert.assertEquals(UrlEscapers.urlFormParameterEscaper().escape("abc-123_z.X"), "abc-123_z.X");
    }

    @Test
    public void testFormParam_empty() {
        Assert.assertEquals(UrlEscapers.urlFormParameterEscaper().escape(""), "");
    }

    // --- urlFragmentEscaper ---

    @Test
    public void testFragment_safeChars() {
        String safe = "abc-._~!$&'()*+,;=:@/?";
        Assert.assertEquals(UrlEscapers.urlFragmentEscaper().escape(safe), safe);
    }

    @Test
    public void testFragment_space() {
        Assert.assertEquals(UrlEscapers.urlFragmentEscaper().escape("hello world"), "hello%20world");
    }

    @Test
    public void testFragment_hash() {
        Assert.assertEquals(UrlEscapers.urlFragmentEscaper().escape("a#b"), "a%23b");
    }

    @Test
    public void testFragment_unicode() {
        Assert.assertEquals(UrlEscapers.urlFragmentEscaper().escape("café"), "caf%C3%A9");
    }

    @Test
    public void testFragment_empty() {
        Assert.assertEquals(UrlEscapers.urlFragmentEscaper().escape(""), "");
    }

    // --- urlPathSegmentEscaper ---

    @Test
    public void testPathSegment_safeChars() {
        String safe = "abc-._~!$&'()*+,;=:@";
        Assert.assertEquals(UrlEscapers.urlPathSegmentEscaper().escape(safe), safe);
    }

    @Test
    public void testPathSegment_slash() {
        Assert.assertEquals(UrlEscapers.urlPathSegmentEscaper().escape("a/b"), "a%2Fb");
    }

    @Test
    public void testPathSegment_questionMark() {
        Assert.assertEquals(UrlEscapers.urlPathSegmentEscaper().escape("a?b"), "a%3Fb");
    }

    @Test
    public void testPathSegment_space() {
        Assert.assertEquals(UrlEscapers.urlPathSegmentEscaper().escape("hello world"), "hello%20world");
    }

    @Test
    public void testPathSegment_empty() {
        Assert.assertEquals(UrlEscapers.urlPathSegmentEscaper().escape(""), "");
    }

    @Test
    public void testPathSegment_unicode() {
        Assert.assertEquals(UrlEscapers.urlPathSegmentEscaper().escape("日本"), "%E6%97%A5%E6%9C%AC");
    }
}
