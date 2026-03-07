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

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.jooby.MediaType;
import org.jooby.test.MockUnit;
import org.junit.Test;

public class InputStreamAssetTest {

  @Test
  public void defaults() throws Exception {
    new MockUnit(InputStream.class)
        .run(unit -> {
          InputStreamAsset asset =
              new InputStreamAsset(
                  unit.get(InputStream.class),
                  "stream.bin",
                  MediaType.octetstream
              );
          assertEquals(-1, asset.lastModified());
          assertEquals(-1, asset.length());
          assertEquals("stream.bin", asset.name());
          assertEquals("stream.bin", asset.path());
          assertEquals(unit.get(InputStream.class), asset.stream());
          assertEquals(MediaType.octetstream, asset.type());
        });
  }

  @Test(expected = UnsupportedOperationException.class)
  public void noResource() throws Exception {
    new MockUnit(InputStream.class)
        .run(unit -> {
          new InputStreamAsset(
              unit.get(InputStream.class),
              "stream.bin",
              MediaType.octetstream
          ).resource();
        });
  }

}
