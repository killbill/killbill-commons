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

package org.killbill.commons.utils.escape;

import org.killbill.commons.utils.html.HtmlEscapers;
import org.testng.Assert;
import org.testng.annotations.Test;

public class EscaperTest {

    @Test
    public void testFunctionalInterface() {
        Escaper escaper = String::toUpperCase;
        Assert.assertEquals(escaper.escape("hello"), "HELLO");
    }

    @Test
    public void testMethodReference() {
        Escaper html = HtmlEscapers.htmlEscaper();
        java.util.function.Function<String, String> fn = html::escape;
        Assert.assertEquals(fn.apply("<b>"), "&lt;b&gt;");
    }
}
