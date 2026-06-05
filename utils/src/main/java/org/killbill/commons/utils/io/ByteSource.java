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

package org.killbill.commons.utils.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * A lightweight byte source abstraction for lazily concatenating and slicing byte arrays.
 *
 * <p>Inspired by Guava's {@code com.google.common.io.ByteSource}, but intentionally minimal:
 * only the subset used by jooby's SseRenderer (wrap, concat, empty, slice, read) is implemented.
 * Internally backed by byte arrays and {@link ByteArrayOutputStream} — no streaming, no IO
 * dependency tree.</p>
 *
 * @see <a href="https://guava.dev/releases/31.0.1-jre/api/docs/com/google/common/io/ByteSource.html">
 *     Original Guava ByteSource</a>
 */
public abstract class ByteSource {

    private static final ByteSource EMPTY = new ByteSource() {
        @Override
        public byte[] read() {
            return new byte[0];
        }

        @Override
        public String toString() {
            return "ByteSource.empty()";
        }
    };

    /**
     * Returns a {@code ByteSource} that wraps the given byte array.
     */
    public static ByteSource wrap(final byte[] bytes) {
        return new ByteSource() {
            @Override
            public byte[] read() {
                return bytes.clone();
            }

            @Override
            public String toString() {
                return "ByteSource.wrap(" + bytes.length + " bytes)";
            }
        };
    }

    /**
     * Returns an empty {@code ByteSource}.
     */
    public static ByteSource empty() {
        return EMPTY;
    }

    /**
     * Returns a {@code ByteSource} that concatenates all given sources.
     * Bytes are materialized on {@link #read()}.
     */
    public static ByteSource concat(final ByteSource... sources) {
        return new ByteSource() {
            @Override
            public byte[] read() throws IOException {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (final ByteSource source : sources) {
                    out.write(source.read());
                }
                return out.toByteArray();
            }

            @Override
            public String toString() {
                return "ByteSource.concat(" + sources.length + " sources)";
            }
        };
    }

    /**
     * Returns a view of a sub-range of this source.
     * If offset + length exceeds actual size, returns only available bytes from offset.
     */
    public ByteSource slice(final long offset, final long length) {
        final ByteSource parent = this;
        return new ByteSource() {
            @Override
            public byte[] read() throws IOException {
                final byte[] all = parent.read();
                final int start = (int) Math.min(offset, all.length);
                final int end = (int) Math.min(offset + length, all.length);
                return Arrays.copyOfRange(all, start, end);
            }

            @Override
            public String toString() {
                return parent.toString() + ".slice(" + offset + ", " + length + ")";
            }
        };
    }

    /**
     * Reads the full contents as a byte array.
     */
    public abstract byte[] read() throws IOException;
}
