/*
 * Copyright (C) 2021 Apache Software Foundation
 * Copyright (C) 2010 The Guava Authors
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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.CheckForNull;

/**
 * Verbatim copy to guava's Strings (v.31.0.1). <a href="https://github.com/killbill/killbill/issues/1615">See more</a>
 * about this.
 */
public final class Strings {

    public static boolean isNullOrEmpty(final String string) {
        return string == null || string.isEmpty();
    }

    /**
     * Returns the given string if it is nonempty; {@code null} otherwise.
     *
     * @param string the string to test and possibly return
     * @return {@code string} itself if it is nonempty; {@code null} if it is empty or null
     */
    public static String emptyToNull(final String string) {
        return isNullOrEmpty(string) ? null : string;
    }

    /**
     * Do what {@link String#split(String)} do, additionally filter empty/blank string and trim it.
     * A replacement for Guava's <pre>Splitter.on(',').omitEmptyStrings().trimResults();</pre>
     */
    public static List<String> split(final String string, final String separator) {
        if (isNullOrEmpty(string)) {
            return Collections.emptyList();
        }

        return Stream.of(string.split(separator))
                .filter(s -> !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns the given string if it is non-null; the empty string otherwise.
     *
     * @param string the string to test and possibly return
     * @return {@code string} itself if it is non-null; {@code ""} if it is null
     */
    public static String nullToEmpty(@CheckForNull final String string) {
        return (string == null) ? "" : string;
    }

    /**
     * Return true if {@code str} contains upper-case.
     */
    public static boolean containsUpperCase(final String str) {
        if (isNullOrEmpty(str)) {
            return false;
        }
        return !str.equals(str.toLowerCase());
    }

    /**
     * Convert string to camel case, based on {@code delimiter}. Taken from apache common-text
     * <a href="https://raw.githubusercontent.com/apache/commons-text/master/src/main/java/org/apache/commons/text/CaseUtils.java">CaseUtils</a>
     */
    public static String toCamelCase(String str, final boolean capitalizeFirstLetter, final char... delimiters) {
        if (str == null || str.isBlank()) {
            return str;
        }
        str = str.toLowerCase();
        final int strLen = str.length();
        final int[] newCodePoints = new int[strLen];
        int outOffset = 0;
        final Set<Integer> delimiterSet = toDelimiterSet(delimiters);
        boolean capitalizeNext = capitalizeFirstLetter;
        for (int index = 0; index < strLen; ) {
            final int codePoint = str.codePointAt(index);

            if (delimiterSet.contains(codePoint)) {
                capitalizeNext = outOffset != 0;
                index += Character.charCount(codePoint);
            } else if (capitalizeNext || outOffset == 0 && capitalizeFirstLetter) {
                final int titleCaseCodePoint = Character.toTitleCase(codePoint);
                newCodePoints[outOffset++] = titleCaseCodePoint;
                index += Character.charCount(titleCaseCodePoint);
                capitalizeNext = false;
            } else {
                newCodePoints[outOffset++] = codePoint;
                index += Character.charCount(codePoint);
            }
        }

        return new String(newCodePoints, 0, outOffset);
    }

    private static Set<Integer> toDelimiterSet(final char[] delimiters) {
        final Set<Integer> delimiterHashSet = new HashSet<>();
        delimiterHashSet.add(Character.codePointAt(new char[]{' '}, 0));
        if (delimiters == null || delimiters.length == 0) {
            return delimiterHashSet;
        }

        for (int index = 0; index < delimiters.length; index++) {
            delimiterHashSet.add(Character.codePointAt(delimiters, index));
        }
        return delimiterHashSet;
    }

    /**
     * Replace string from camel case to snake case, eg: "thisIsASentence" to "this_is_a_sentence".
     */
    // https://stackoverflow.com/a/57632022
    public static String toSnakeCase(final String str) {
        final StringBuilder result = new StringBuilder();

        boolean lastUppercase = false;

        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            final char lastEntry = i == 0 ? 'X' : result.charAt(result.length() - 1);
            // ch == ' ' || ch == '_' || ch == '-' || ch == '.'
            if (ch == '_') {
                lastUppercase = false;

                if (lastEntry == '_') {
                    continue;
                } else {
                    ch = '_';
                }
            } else if (Character.isUpperCase(ch)) {
                ch = Character.toLowerCase(ch);
                // is start?
                if (i > 0) {
                    if (lastUppercase) {
                        // test if end of acronym
                        if (i + 1 < str.length()) {
                            char next = str.charAt(i + 1);
                            if (!Character.isUpperCase(next) && Character.isAlphabetic(next)) {
                                // end of acronym
                                if (lastEntry != '_') {
                                    result.append('_');
                                }
                            } else {
                                result.append('_');
                            }
                        }
                    } else {
                        // last was lowercase, insert _
                        if (lastEntry != '_') {
                            result.append('_');
                        }
                    }
                }
                lastUppercase = true;
            } else {
                lastUppercase = false;
            }

            result.append(ch);
        }
        return result.toString();
    }
}
