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

package org.killbill.commons.utils.html;

import org.testng.Assert;
import org.testng.annotations.Test;

public class HtmlEscapersTest {

    @Test
    public void testAmpersand() {
        Assert.assertEquals(HtmlEscapers.htmlEscaper().escape("a&b"), "a&amp;b");
    }

    @Test
    public void testLessThan() {
        Assert.assertEquals(HtmlEscapers.htmlEscaper().escape("<script>"), "&lt;script&gt;");
    }

    @Test
    public void testDoubleQuote() {
        Assert.assertEquals(HtmlEscapers.htmlEscaper().escape("say \"hi\""), "say &quot;hi&quot;");
    }

    @Test
    public void testSingleQuote() {
        Assert.assertEquals(HtmlEscapers.htmlEscaper().escape("it's"), "it&#39;s");
    }

    @Test
    public void testNoEscapingNeeded() {
        Assert.assertEquals(HtmlEscapers.htmlEscaper().escape("hello world"), "hello world");
    }

    @Test
    public void testAllSpecialChars() {
        Assert.assertEquals(
                HtmlEscapers.htmlEscaper().escape("&<>\"'"),
                "&amp;&lt;&gt;&quot;&#39;");
    }

    @Test
    public void testEmptyString() {
        Assert.assertEquals(HtmlEscapers.htmlEscaper().escape(""), "");
    }

    @Test
    public void testSameInstance() {
        Assert.assertSame(HtmlEscapers.htmlEscaper(), HtmlEscapers.htmlEscaper());
    }
}
