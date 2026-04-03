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

import static org.mockito.Mockito.when;
import org.jooby.test.MockUnit;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

public class AssetForwardingTest {

  @Test
  public void etag() throws Exception {
    new MockUnit(Asset.class)
        .expect(unit -> {
          Asset asset = unit.get(Asset.class);
          when(asset.etag()).thenReturn("tag");
        })
        .run(unit -> {
          assertEquals("tag", new Asset.Forwarding(unit.get(Asset.class)).etag());
        });
  }

  @Test
  public void lastModified() throws Exception {
    new MockUnit(Asset.class)
        .expect(unit -> {
          Asset asset = unit.get(Asset.class);
          when(asset.lastModified()).thenReturn(1L);
        })
        .run(unit -> {
          assertEquals(1L, new Asset.Forwarding(unit.get(Asset.class)).lastModified());
        });
  }

  @Test
  public void len() throws Exception {
    new MockUnit(Asset.class)
        .expect(unit -> {
          Asset asset = unit.get(Asset.class);
          when(asset.length()).thenReturn(1L);
        })
        .run(unit -> {
          assertEquals(1L, new Asset.Forwarding(unit.get(Asset.class)).length());
        });
  }

  @Test
  public void name() throws Exception {
    new MockUnit(Asset.class)
        .expect(unit -> {
          Asset asset = unit.get(Asset.class);
          when(asset.name()).thenReturn("n");
        })
        .run(unit -> {
          assertEquals("n", new Asset.Forwarding(unit.get(Asset.class)).name());
        });
  }

  @Test
  public void path() throws Exception {
    new MockUnit(Asset.class)
        .expect(unit -> {
          Asset asset = unit.get(Asset.class);
          when(asset.path()).thenReturn("p");
        })
        .run(unit -> {
          assertEquals("p", new Asset.Forwarding(unit.get(Asset.class)).path());
        });
  }

  @Test
  public void url() throws Exception {
    URL url = new File("pom.xml").toURI().toURL();
    new MockUnit(Asset.class)
        .expect(unit -> {
          Asset asset = unit.get(Asset.class);
          when(asset.resource()).thenReturn(url);
        })
        .run(unit -> {
          assertEquals(url, new Asset.Forwarding(unit.get(Asset.class)).resource());
        });
  }

  @Test
  public void stream() throws Exception {
    new MockUnit(Asset.class, InputStream.class)
        .expect(unit -> {
          Asset asset = unit.get(Asset.class);
          when(asset.stream()).thenReturn(unit.get(InputStream.class));
        })
        .run(unit -> {
          assertEquals(unit.get(InputStream.class),
              new Asset.Forwarding(unit.get(Asset.class)).stream());
        });
  }

  @Test
  public void type() throws Exception {
    new MockUnit(Asset.class)
        .expect(unit -> {
          Asset asset = unit.get(Asset.class);
          when(asset.type()).thenReturn(MediaType.css);
        })
        .run(unit -> {
          assertEquals(MediaType.css, new Asset.Forwarding(unit.get(Asset.class)).type());
        });
  }

}
