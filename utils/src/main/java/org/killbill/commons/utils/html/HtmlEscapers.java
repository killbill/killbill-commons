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

package org.killbill.commons.utils.html;

import org.killbill.commons.utils.escape.Escaper;

/**
 * Provides HTML escaping. Replaces the 5 characters that have special meaning in HTML
 * ({@code & < > " '}) with their corresponding HTML entities.
 *
 * @see <a href="https://guava.dev/releases/31.0.1-jre/api/docs/com/google/common/html/HtmlEscapers.html">
 *     Original Guava HtmlEscapers</a>
 */
public final class HtmlEscapers {

    private HtmlEscapers() {}

    private static final Escaper HTML_ESCAPER = input -> {
        final StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#39;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    };

    /**
     * Returns an {@link Escaper} that replaces special HTML characters with their entity
     * equivalents.
     */
    public static Escaper htmlEscaper() {
        return HTML_ESCAPER;
    }
}
