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

import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;

import org.jooby.MediaType;
import org.junit.Test;

public class SseRendererTest {

  @Test(expected = UnsupportedOperationException.class)
  public void unsupportedSendFile() throws Exception {
    FileChannel filechannel = null;
    new SseRenderer(Collections.emptyList(), MediaType.ALL, StandardCharsets.UTF_8, Locale.US,
        Collections.emptyMap())
            ._send(filechannel);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void unsupportedStream() throws Exception {
    InputStream stream = null;
    new SseRenderer(Collections.emptyList(), MediaType.ALL, StandardCharsets.UTF_8, Locale.US,
        Collections.emptyMap())
            ._send(stream);
  }
}
