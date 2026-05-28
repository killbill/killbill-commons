/*
 * Copyright (C) 2008 The Guava Authors
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
 * Verbatim copy of guava's com.google.common.base.CharMatcher (v.31.0.1) — minimal subset used by
 * CaseFormat. See <a href="https://github.com/killbill/killbill/issues/1615">More</a>.
 *
 * <p>Determines a true or false value for any Java {@code char} value, just as {@link
 * java.util.function.Predicate} does for any {@link Object}.
 *
 * @author Kevin Bourrillion
 * @since 1.0
 */
public abstract class CharMatcher {

    /** Returns a {@code char} matcher that matches only one specified BMP character. */
    public static CharMatcher is(final char match) {
        return new Is(match);
    }

    /**
     * Returns a {@code char} matcher that matches any character in a given BMP range (both endpoints
     * are inclusive). For example, to match any lowercase letter of the English alphabet, use {@code
     * CharMatcher.inRange('a', 'z')}.
     *
     * @throws IllegalArgumentException if {@code endInclusive < startInclusive}
     */
    public static CharMatcher inRange(final char startInclusive, final char endInclusive) {
        return new InRange(startInclusive, endInclusive);
    }

    /** Determines a true or false value for the given character. */
    public abstract boolean matches(char c);

    /**
     * Returns the index of the first matching character in a character sequence, starting from a
     * given position, or {@code -1} if no character matches after that position.
     */
    public int indexIn(CharSequence sequence, int start) {
        int length = sequence.length();
        Preconditions.checkPositionIndex(start, length);
        for (int i = start; i < length; i++) {
            if (matches(sequence.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    // Implementation of is(char)
    private static final class Is extends CharMatcher {

        private final char match;

        Is(char match) {
            this.match = match;
        }

        @Override
        public boolean matches(char c) {
            return c == match;
        }

        @Override
        public String toString() {
            return "CharMatcher.is('" + match + "')";
        }
    }

    // Implementation of inRange(char, char)
    private static final class InRange extends CharMatcher {

        private final char startInclusive;
        private final char endInclusive;

        InRange(char startInclusive, char endInclusive) {
            Preconditions.checkArgument(endInclusive >= startInclusive);
            this.startInclusive = startInclusive;
            this.endInclusive = endInclusive;
        }

        @Override
        public boolean matches(char c) {
            return startInclusive <= c && c <= endInclusive;
        }

        @Override
        public String toString() {
            return "CharMatcher.inRange('" + startInclusive + "', '" + endInclusive + "')";
        }
    }
}
