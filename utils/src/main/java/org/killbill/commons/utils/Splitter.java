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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A string splitter with fluent API, inspired by Guava's {@code Splitter}.
 *
 * <p>Like Guava's implementation, this does NOT use regex internally. Splitting is done by
 * scanning for separator characters directly. This avoids regex compilation overhead and
 * eliminates escaping pitfalls (e.g. splitting on '.' with regex would match any character).</p>
 *
 * <p>Only the subset used by jooby is implemented: {@link #on(char)}, {@link #on(CharMatcher)},
 * {@link #trimResults()}, {@link #omitEmptyStrings()}, {@link #split(CharSequence)},
 * and {@link #splitToList(CharSequence)}.</p>
 *
 * @see <a href="https://guava.dev/releases/31.0.1-jre/api/docs/com/google/common/base/Splitter.html">
 *     Original Guava Splitter</a>
 */
public final class Splitter {

    private final CharMatcher separatorMatcher;
    private final boolean trimResults;
    private final boolean omitEmpty;

    private Splitter(final CharMatcher separatorMatcher, final boolean trimResults, final boolean omitEmpty) {
        this.separatorMatcher = separatorMatcher;
        this.trimResults = trimResults;
        this.omitEmpty = omitEmpty;
    }

    /**
     * Returns a splitter that splits on the given single character.
     * No regex is used — the character is matched literally, consistent with Guava's behavior.
     */
    public static Splitter on(final char separator) {
        return new Splitter(new CharMatcher() {
            @Override
            public boolean matches(final char c) {
                return c == separator;
            }
        }, false, false);
    }

    /**
     * Returns a splitter that splits on any character matched by the given {@link CharMatcher}.
     * No regex is used — each character is tested against the matcher directly.
     */
    public static Splitter on(final CharMatcher separatorMatcher) {
        return new Splitter(separatorMatcher, false, false);
    }

    /**
     * Returns a splitter that trims whitespace from the beginning and end of each resulting part.
     */
    public Splitter trimResults() {
        return new Splitter(this.separatorMatcher, true, this.omitEmpty);
    }

    /**
     * Returns a splitter that omits empty strings from the results (after trimming, if applicable).
     */
    public Splitter omitEmptyStrings() {
        return new Splitter(this.separatorMatcher, this.trimResults, true);
    }

    /**
     * Splits the given sequence into parts and returns them as an {@link Iterable}.
     */
    public Iterable<String> split(final CharSequence sequence) {
        return splitToList(sequence);
    }

    /**
     * Splits the given sequence into parts and returns them as an unmodifiable {@link List}.
     */
    public List<String> splitToList(final CharSequence sequence) {
        final List<String> raw = splitRaw(sequence);
        final List<String> result = new ArrayList<>();
        for (final String part : raw) {
            String value = trimResults ? part.trim() : part;
            if (omitEmpty && value.isEmpty()) {
                continue;
            }
            result.add(value);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Splits the sequence by scanning for separator characters. No regex involved.
     */
    private List<String> splitRaw(final CharSequence sequence) {
        final List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < sequence.length(); i++) {
            if (separatorMatcher.matches(sequence.charAt(i))) {
                parts.add(sequence.subSequence(start, i).toString());
                start = i + 1;
            }
        }
        parts.add(sequence.subSequence(start, sequence.length()).toString());
        return parts;
    }
}
