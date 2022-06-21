/*
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

package org.killbill.commons.utils.io;

import java.io.IOException;
import java.io.Reader;

import org.killbill.commons.utils.Preconditions;

/**
 * Contains verbatim copy to guava's Joiner (v.31.0.1). See <a href="https://github.com/killbill/killbill/issues/1615">More</a>.
 */
public final class CharStreams {

    private static final int DEFAULT_BUF_SIZE = 0x800;

    /**
     * Reads all characters from a {@link Readable} object into a {@link String}. Does not close the {@code Readable}.
     *
     * @param r the object to read from
     * @return a string containing all the characters
     * @throws IOException if an I/O error occurs
     */
    public static String toString(final Readable r) throws IOException {
        return toStringBuilder(r).toString();
    }

    /**
     * Warning: Not like Guava's {@code com.google.common.io.CharStreams#toStringBuilder(Readable)}, parameter of this
     * method only accept instance of {@link Reader}, otherwise it will throw an Exception. Method parameter type
     * preserved here to make sure easier to track it back to Guava, if needed.
     *
     * Reads all characters from a {@link Readable} object into a new {@link StringBuilder} instance. Does not close
     * the {@code Readable}.
     *
     * @param r the object to read from
     * @return a {@link StringBuilder} containing all the characters
     * @throws IOException if an I/O error occurs
     */
    private static StringBuilder toStringBuilder(final Readable r) throws IOException {
        final StringBuilder sb = new StringBuilder();
        if (r instanceof Reader) {
            copyReaderToBuilder((Reader) r, sb);
        } else {
            throw new RuntimeException("IOUtils#toStringBuilder() parameter should be instance of java.io.Reader");
        }
        return sb;
    }

    static long copyReaderToBuilder(final Reader from, final StringBuilder to) throws IOException {
        Preconditions.checkNotNull(from);
        Preconditions.checkNotNull(to);
        char[] buf = new char[DEFAULT_BUF_SIZE];
        int nRead;
        long total = 0;
        while ((nRead = from.read(buf)) != -1) {
            to.append(buf, 0, nRead);
            total += nRead;
        }
        return total;
    }
}
