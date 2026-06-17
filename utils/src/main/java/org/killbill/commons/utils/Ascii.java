/*
 * Copyright (C) 2010 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.killbill.commons.utils;

/**
 * Verbatim copy of guava's com.google.common.base.Ascii (v.31.0.1) — case-conversion methods only.
 * See <a href="https://github.com/killbill/killbill/issues/1615">More</a>.
 *
 * <p>Static methods pertaining to ASCII characters (those in the range of values {@code 0x00}
 * through {@code 0x7F}), and to strings containing such characters.
 *
 * @author Catherine Berry
 * @author Gregory Kick
 * @since 7.0
 */
public final class Ascii {

    private Ascii() {}

    /**
     * The difference between the ASCII values of an uppercase character and its lowercase
     * counterpart. The value is 0x20 (decimal: 32).
     */
    private static final char CASE_MASK = 0x20;

    /**
     * Returns a copy of the input string in which all {@linkplain #isUpperCase(char) uppercase ASCII
     * characters} have been converted to lowercase. All other characters are copied without
     * modification.
     */
    public static String toLowerCase(String string) {
        int length = string.length();
        for (int i = 0; i < length; i++) {
            if (isUpperCase(string.charAt(i))) {
                char[] chars = string.toCharArray();
                for (; i < length; i++) {
                    char c = chars[i];
                    if (isUpperCase(c)) {
                        chars[i] = (char) (c ^ CASE_MASK);
                    }
                }
                return String.valueOf(chars);
            }
        }
        return string;
    }

    /**
     * Returns a copy of the input character sequence in which all {@linkplain #isUpperCase(char)
     * uppercase ASCII characters} have been converted to lowercase. All other characters are copied
     * without modification.
     *
     * @since 14.0
     */
    public static String toLowerCase(CharSequence chars) {
        if (chars instanceof String) {
            return toLowerCase((String) chars);
        }
        char[] newChars = new char[chars.length()];
        for (int i = 0; i < newChars.length; i++) {
            newChars[i] = toLowerCase(chars.charAt(i));
        }
        return String.valueOf(newChars);
    }

    /**
     * If the argument is an {@linkplain #isUpperCase(char) uppercase ASCII character}, returns the
     * lowercase equivalent. Otherwise returns the argument.
     */
    public static char toLowerCase(char c) {
        return isUpperCase(c) ? (char) (c ^ CASE_MASK) : c;
    }

    /**
     * Returns a copy of the input string in which all {@linkplain #isLowerCase(char) lowercase ASCII
     * characters} have been converted to uppercase. All other characters are copied without
     * modification.
     */
    public static String toUpperCase(String string) {
        int length = string.length();
        for (int i = 0; i < length; i++) {
            if (isLowerCase(string.charAt(i))) {
                char[] chars = string.toCharArray();
                for (; i < length; i++) {
                    char c = chars[i];
                    if (isLowerCase(c)) {
                        chars[i] = (char) (c ^ CASE_MASK);
                    }
                }
                return String.valueOf(chars);
            }
        }
        return string;
    }

    /**
     * Returns a copy of the input character sequence in which all {@linkplain #isLowerCase(char)
     * lowercase ASCII characters} have been converted to uppercase. All other characters are copied
     * without modification.
     *
     * @since 14.0
     */
    public static String toUpperCase(CharSequence chars) {
        if (chars instanceof String) {
            return toUpperCase((String) chars);
        }
        char[] newChars = new char[chars.length()];
        for (int i = 0; i < newChars.length; i++) {
            newChars[i] = toUpperCase(chars.charAt(i));
        }
        return String.valueOf(newChars);
    }

    /**
     * If the argument is a {@linkplain #isLowerCase(char) lowercase ASCII character}, returns the
     * uppercase equivalent. Otherwise returns the argument.
     */
    public static char toUpperCase(char c) {
        return isLowerCase(c) ? (char) (c ^ CASE_MASK) : c;
    }

    /**
     * Indicates whether {@code c} is one of the twenty-six lowercase ASCII alphabetic characters
     * between {@code 'a'} and {@code 'z'} inclusive. All others (including non-ASCII characters)
     * return {@code false}.
     */
    public static boolean isLowerCase(char c) {
        // Note: This was benchmarked against the alternate expression "(char)(c - 'a') < 26" (Nov '13)
        // and found to perform at least as well, or better.
        return (c >= 'a') && (c <= 'z');
    }

    /**
     * Indicates whether {@code c} is one of the twenty-six uppercase ASCII alphabetic characters
     * between {@code 'A'} and {@code 'Z'} inclusive. All others (including non-ASCII characters)
     * return {@code false}.
     */
    public static boolean isUpperCase(char c) {
        return (c >= 'A') && (c <= 'Z');
    }
}
