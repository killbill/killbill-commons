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
package org.jooby.internal;

import com.google.common.base.Splitter;
import org.jooby.Err;
import org.jooby.Status;

import java.util.Iterator;
import java.util.function.BiFunction;

public class ByteRange {

  private static final String BYTES_EQ = "bytes=";

  public static long[] parse(final String value) {
    if (!value.startsWith(BYTES_EQ)) {
      throw new Err(Status.REQUESTED_RANGE_NOT_SATISFIABLE, value);
    }
    BiFunction<String, Integer, Long> number = (it, offset) -> {
      try {
        return Long.parseLong(it.substring(offset));
      } catch (NumberFormatException | IndexOutOfBoundsException x) {
        throw new Err(Status.REQUESTED_RANGE_NOT_SATISFIABLE, value);
      }
    };

    Iterator<String> ranges = Splitter.on(',')
      .trimResults()
      .omitEmptyStrings()
      .split(value.substring(BYTES_EQ.length()))
      .iterator();
    if (ranges.hasNext()) {
      String range = ranges.next();
      int dash = range.indexOf('-');
      if (dash == 0) {
        return new long[]{-1L, number.apply(range, 1)};
      } else if (dash > 0) {
        Long start = number.apply(range.substring(0, dash), 0);
        int endidx = dash + 1;
        Long end = endidx < range.length() ? number.apply(range, endidx) : -1L;
        return new long[]{start, end};
      }
    }
    throw new Err(Status.REQUESTED_RANGE_NOT_SATISFIABLE, value);
  }
}
