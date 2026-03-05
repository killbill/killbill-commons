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
package org.jooby;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Test;

public class MediaTypeDbTest {

  @Test
  public void javascript() {
    assertEquals(MediaType.js, MediaType.byExtension("js").get());
    assertEquals(MediaType.js, MediaType.byFile(new File("file.js")).get());
  }

  @Test
  public void css() {
    assertEquals(MediaType.css, MediaType.byExtension("css").get());
    assertEquals(MediaType.css, MediaType.byFile(new File("file.css")).get());
  }

  @Test
  public void json() {
    assertEquals(MediaType.json, MediaType.byExtension("json").get());
    assertEquals(MediaType.json, MediaType.byFile(new File("file.json")).get());
  }

  @Test
  public void png() {
    assertEquals(MediaType.valueOf("image/png"), MediaType.byExtension("png").get());
    assertEquals(MediaType.valueOf("image/png"), MediaType.byFile(new File("file.png")).get());
  }
}
