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

package org.killbill.commons.utils.escape;

/**
 * An object that converts literal text into a format safe for inclusion in a particular context
 * (such as an XML document or a URL). Typically, the reverse process of "unescaping" is performed by
 * a separate interface.
 *
 * <p>Replaces Guava's {@code com.google.common.escape.Escaper}. Made a functional interface
 * so that {@code escaper::escape} method references work naturally.</p>
 *
 * @see <a href="https://guava.dev/releases/31.0.1-jre/api/docs/com/google/common/escape/Escaper.html">
 *     Original Guava Escaper</a>
 */
@FunctionalInterface
public interface Escaper {

    /**
     * Returns the escaped form of the given input string.
     */
    String escape(String input);
}
