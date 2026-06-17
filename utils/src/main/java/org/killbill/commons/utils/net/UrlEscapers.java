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

import org.killbill.commons.utils.escape.Escaper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Provides URL escaping (percent-encoding) for different URL components.
 *
 * <p>Each escaper percent-encodes characters that are not allowed in its respective URL component,
 * as defined by RFC 3986.</p>
 *
 * @see <a href="https://guava.dev/releases/31.0.1-jre/api/docs/com/google/common/net/UrlEscapers.html">
 *     Original Guava UrlEscapers</a>
 */
public final class UrlEscapers {

    private UrlEscapers() {}

    // RFC 3986 unreserved characters: ALPHA / DIGIT / "-" / "." / "_" / "~"
    private static final BitSet UNRESERVED = new BitSet(128);
    // sub-delims: "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
    private static final BitSet SUB_DELIMS = new BitSet(128);

    static {
        // unreserved
        for (char c = 'A'; c <= 'Z'; c++) UNRESERVED.set(c);
        for (char c = 'a'; c <= 'z'; c++) UNRESERVED.set(c);
        for (char c = '0'; c <= '9'; c++) UNRESERVED.set(c);
        UNRESERVED.set('-');
        UNRESERVED.set('.');
        UNRESERVED.set('_');
        UNRESERVED.set('~');

        // sub-delims
        String subDelims = "!$&'()*+,;=";
        for (int i = 0; i < subDelims.length(); i++) {
            SUB_DELIMS.set(subDelims.charAt(i));
        }
    }

    // Path segment safe: unreserved + sub-delims + ":" + "@"
    private static final BitSet PATH_SEGMENT_SAFE = new BitSet(128);

    static {
        PATH_SEGMENT_SAFE.or(UNRESERVED);
        PATH_SEGMENT_SAFE.or(SUB_DELIMS);
        PATH_SEGMENT_SAFE.set(':');
        PATH_SEGMENT_SAFE.set('@');
    }

    // Fragment safe: path segment safe + "/" + "?"
    private static final BitSet FRAGMENT_SAFE = new BitSet(128);

    static {
        FRAGMENT_SAFE.or(PATH_SEGMENT_SAFE);
        FRAGMENT_SAFE.set('/');
        FRAGMENT_SAFE.set('?');
    }

    /**
     * Returns an {@link Escaper} for URL fragment encoding. Characters not allowed in a URI
     * fragment are percent-encoded.
     *
     * <p>Safe characters: unreserved + sub-delims + {@code : @ / ?}</p>
     */
    public static Escaper urlFragmentEscaper() {
        return input -> percentEncode(input, FRAGMENT_SAFE);
    }

    /**
     * Returns an {@link Escaper} for URL form parameter encoding (application/x-www-form-urlencoded).
     * Space is encoded as {@code +}, other unsafe characters as percent-encoded.
     */
    public static Escaper urlFormParameterEscaper() {
        return input -> URLEncoder.encode(input, StandardCharsets.UTF_8);
    }

    /**
     * Returns an {@link Escaper} for URL path segment encoding. Characters not allowed in a path
     * segment are percent-encoded.
     *
     * <p>Safe characters: unreserved + sub-delims + {@code : @}</p>
     */
    public static Escaper urlPathSegmentEscaper() {
        return input -> percentEncode(input, PATH_SEGMENT_SAFE);
    }

    private static String percentEncode(final String input, final BitSet safeChars) {
        final StringBuilder sb = new StringBuilder(input.length());
        final byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        for (final byte b : bytes) {
            final int unsigned = b & 0xFF;
            if (unsigned < 128 && safeChars.get(unsigned)) {
                sb.append((char) unsigned);
            } else {
                sb.append('%');
                sb.append(Character.toUpperCase(Character.forDigit(unsigned >> 4, 16)));
                sb.append(Character.toUpperCase(Character.forDigit(unsigned & 0xF, 16)));
            }
        }
        return sb.toString();
    }
}
