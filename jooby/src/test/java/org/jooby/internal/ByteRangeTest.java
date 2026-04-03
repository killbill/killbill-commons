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

import org.jooby.Err;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ByteRangeTest {

  @Test
  public void newInstance() {
    new ByteRange();
  }

  @Test(expected = Err.class)
  public void noByteRange() {
    ByteRange.parse("foo");
  }

  @Test(expected = Err.class)
  public void emptyRange() {
    ByteRange.parse("byte=");
  }

  @Test(expected = Err.class)
  public void invalidRange() {
    ByteRange.parse("bytes=-");
  }

  @Test(expected = Err.class)
  public void invalidRange2() {
    ByteRange.parse("bytes=z-");
  }

  @Test(expected = Err.class)
  public void invalidRange3() {
    ByteRange.parse("bytes=-z");
  }

  @Test(expected = Err.class)
  public void invalidRange4() {
    ByteRange.parse("bytes=6");
  }

  @Test
  public void validRange() {
    long[] range = ByteRange.parse("bytes=1-10");
    assertEquals(1L, range[0]);
    assertEquals(10L, range[1]);
  }

  @Test
  public void prefixRange() {
    long[] range = ByteRange.parse("bytes=99-");
    assertEquals(99L, range[0]);
    assertEquals(-1L, range[1]);
  }

  @Test
  public void suffixRange() {
    long[] range = ByteRange.parse("bytes=-99");
    assertEquals(-1L, range[0]);
    assertEquals(99L, range[1]);
  }

}
