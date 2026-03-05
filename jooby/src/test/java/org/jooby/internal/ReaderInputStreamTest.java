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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;

public class ReaderInputStreamTest {

  @Test
  public void empty() throws IOException {
    try (ReaderInputStream reader = new ReaderInputStream(new StringReader(""), UTF_8)) {
      assertEquals(-1, reader.read());
    }

  }

  @Test
  public void one() throws IOException {
    try (ReaderInputStream reader = new ReaderInputStream(new StringReader("a"), UTF_8)) {
      assertEquals(97, reader.read());
    }
  }

  @Test
  public void read0() throws IOException {
    try (ReaderInputStream reader = new ReaderInputStream(new StringReader("a"), UTF_8)) {
      assertEquals(0, reader.read(new byte[0]));
    }
  }

}
