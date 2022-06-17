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
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Queue;

import org.killbill.commons.utils.Preconditions;
import org.killbill.commons.utils.math.IntMath;

/**
 * Contains verbatim copy to guava's Joiner (v.31.0.1). See <a href="https://github.com/killbill/killbill/issues/1615">More</a>.
 */
public final class ByteStreams {

    private static final int BUFFER_SIZE = 8192;

    /** Max array length on JVM. */
    private static final int MAX_ARRAY_LEN = Integer.MAX_VALUE - 8;

    /** Large enough to never need to expand, given the geometric progression of buffer sizes. */
    private static final int TO_BYTE_ARRAY_DEQUE_SIZE = 20;

    /**
     * Reads all bytes from an input stream into a byte array. Does not close the stream.
     *
     * @param in the input stream to read from
     * @return a byte array containing all the bytes from the stream
     * @throws IOException if an I/O error occurs
     */
    public static byte[] toByteArray(final InputStream in) throws IOException {
        Preconditions.checkNotNull(in);
        return toByteArrayInternal(in, new ArrayDeque<>(TO_BYTE_ARRAY_DEQUE_SIZE), 0);
    }

    /**
     * Returns a byte array containing the bytes from the buffers already in {@code bufs} (which have
     * a total combined length of {@code totalLen} bytes) followed by all bytes remaining in the given
     * input stream.
     */
    private static byte[] toByteArrayInternal(final InputStream in, final Queue<byte[]> bufs, int totalLen) throws IOException {
        // Starting with an 8k buffer, double the size of each successive buffer. Buffers are retained
        // in a deque so that there's no copying between buffers while reading and so all of the bytes
        // in each new allocated buffer are available for reading from the stream.
        for (int bufSize = BUFFER_SIZE; totalLen < MAX_ARRAY_LEN; bufSize = IntMath.saturatedMultiply(bufSize, 2)) {
            final byte[] buf = new byte[Math.min(bufSize, MAX_ARRAY_LEN - totalLen)];
            bufs.add(buf);
            int off = 0;
            while (off < buf.length) {
                // always OK to fill buf; its size plus the rest of bufs is never more than MAX_ARRAY_LEN
                final int r = in.read(buf, off, buf.length - off);
                if (r == -1) {
                    return combineBuffers(bufs, totalLen);
                }
                off += r;
                totalLen += r;
            }
        }

        // read MAX_ARRAY_LEN bytes without seeing end of stream
        if (in.read() == -1) {
            // oh, there's the end of the stream
            return combineBuffers(bufs, MAX_ARRAY_LEN);
        } else {
            throw new OutOfMemoryError("input is too large to fit in a byte array");
        }
    }

    private static byte[] combineBuffers(final Queue<byte[]> bufs, final int totalLen) {
        final byte[] result = new byte[totalLen];
        int remaining = totalLen;
        while (remaining > 0) {
            final byte[] buf = bufs.remove();
            final int bytesToCopy = Math.min(remaining, buf.length);
            final int resultOffset = totalLen - remaining;
            System.arraycopy(buf, 0, result, resultOffset, bytesToCopy);
            remaining -= bytesToCopy;
        }
        return result;
    }
}
