/*
 * Copyright 2010-2012 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.killbill.commons.utils.Preconditions;

/**
 * FIXME-1615: revert IOUtils to previous state.
 *
 * This class originally only contains {@link #toString(InputStream)} method. When working on 1615, this class host
 * several static methods from Guava's common.io package. Revert this class to
 */
public final class IOUtils {

    public static String toString(final InputStream inputStream) throws IOException {
        final String result;
        try (inputStream) {
            result = new String(ByteStreams.toByteArray(inputStream), StandardCharsets.UTF_8);
        }
        return result;
    }

    /**
     * DEPRECATED. Favor {@link ByteStreams} instead of this class.
     *
     * Reads all bytes from an input stream into a byte array. Does not close the stream.
     *
     * @param in the input stream to read from
     * @return a byte array containing all the bytes from the stream
     * @throws IOException if an I/O error occurs
     */
    @Deprecated
    public static byte[] toByteArray(final InputStream in) throws IOException {
        Preconditions.checkNotNull(in);
        return ByteStreams.toByteArray(in);
    }

    // -- Verbatim copy of Guava 31.0.1 (com.google.common.io.Resources#getResource(String))

    /**
     * DEPRECATED. Favor {@link Resources} instead of this class for easy and "backward searchability" with Guava.
     *
     * Returns a {@code URL} pointing to {@code resourceName} if the resource is found using the
     * {@linkplain Thread#getContextClassLoader() context class loader}. In simple environments, the
     * context class loader will find resources from the class path. In environments where different
     * threads can have different class loaders, for example app servers, the context class loader
     * will typically have been set to an appropriate loader for the current thread.
     *
     * <p>In the unusual case where the context class loader is null, the class loader that loaded
     * this class ({@code Resources}) will be used instead.
     *
     * @throws IllegalArgumentException if the resource is not found
     */
    @Deprecated
    public static URL getResourceAsURL(final String resourceName) {
        return Resources.getResource(resourceName);
    }

    // -- Verbatim Copy of CharStreams

    // 2K chars (4K bytes)
    private static final int DEFAULT_BUF_SIZE = 0x800;

    /**
     * DEPRECATED. Favor {@link CharStreams} instead of this class for easy and "backward searchability" with Guava.
     *
     * Reads all characters from a {@link Readable} object into a {@link String}. Does not close the
     * {@code Readable}.
     *
     * @param r the object to read from
     * @return a string containing all the characters
     * @throws IOException if an I/O error occurs
     */
    @Deprecated
    public static String toString(final Readable r) throws IOException {
        return toStringBuilder(r).toString();
    }

    /**
     * Warning: Not like Guava's {@code com.google.common.io.CharStreams#toStringBuilder(Readable)},
     * parameter of this method only accept instance of {@link Reader}, otherwise it will throw an Exception. Method
     * parameter type preserved here to make sure easier to track it back to Guava, if needed.
     *
     * Reads all characters from a {@link Readable} object into a new {@link StringBuilder} instance.
     * Does not close the {@code Readable}.
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
