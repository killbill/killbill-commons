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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.net.URL;

import org.junit.Test;

public class AssetTest {

  private Asset createAsset(final URL resource, final long lastModified, final long length) {
    return new Asset() {
      @Override
      public String path() {
        return resource.getPath();
      }

      @Override
      public URL resource() {
        return resource;
      }

      @Override
      public long lastModified() {
        return lastModified;
      }

      @Override
      public long length() {
        return length;
      }

      @Override
      public InputStream stream() {
        return null;
      }

      @Override
      public MediaType type() {
        return MediaType.js;
      }
    };
  }

  @Test
  public void etagFormat() throws Exception {
    URL url = new URL("file:///assets/app.js");
    Asset asset = createAsset(url, 1700000000000L, 4096L);

    String etag = asset.etag();
    // Weak etag format: W/"<base64><base64>"
    assertTrue("Should start with W/\"", etag.startsWith("W/\""));
    assertTrue("Should end with \"", etag.endsWith("\""));
  }

  @Test
  public void etagDeterministic() throws Exception {
    URL url = new URL("file:///assets/app.js");
    Asset a1 = createAsset(url, 1700000000000L, 4096L);
    Asset a2 = createAsset(url, 1700000000000L, 4096L);

    assertEquals("Same inputs must produce same etag", a1.etag(), a2.etag());
  }

  @Test
  public void etagSensitiveToLastModified() throws Exception {
    URL url = new URL("file:///assets/app.js");
    Asset a1 = createAsset(url, 1700000000000L, 4096L);
    Asset a2 = createAsset(url, 1700000001000L, 4096L);

    assertNotEquals("Different lastModified must produce different etag", a1.etag(), a2.etag());
  }

  @Test
  public void etagSensitiveToLength() throws Exception {
    URL url = new URL("file:///assets/app.js");
    Asset a1 = createAsset(url, 1700000000000L, 4096L);
    Asset a2 = createAsset(url, 1700000000000L, 8192L);

    assertNotEquals("Different length must produce different etag", a1.etag(), a2.etag());
  }

  @Test
  public void etagSensitiveToResource() throws Exception {
    URL url1 = new URL("file:///assets/app.js");
    URL url2 = new URL("file:///assets/vendor.js");
    Asset a1 = createAsset(url1, 1700000000000L, 4096L);
    Asset a2 = createAsset(url2, 1700000000000L, 4096L);

    assertNotEquals("Different resource must produce different etag", a1.etag(), a2.etag());
  }

  @Test
  public void etagExactValue() throws Exception {
    // Pin a specific input and verify exact output to detect regressions.
    // This value was captured from the original Guava (BaseEncoding + Longs) implementation.
    URL url = new URL("file:///assets/app.js");
    Asset asset = createAsset(url, 1700000000000L, 4096L);

    String expected = "W/\"///+dC0YuPc=/////+L9wPc=\"";
    assertEquals(expected, asset.etag());
  }
}
