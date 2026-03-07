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

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.jooby.MediaType;
import org.jooby.Renderer;
import org.jooby.test.MockUnit;
import org.jooby.test.MockUnit.Block;
import org.junit.Test;

public class BytesRendererTest {

  private Block defaultType = unit -> {
    Renderer.Context ctx = unit.get(Renderer.Context.class);
    expect(ctx.type(MediaType.octetstream)).andReturn(ctx);
  };

  @Test
  public void render() throws Exception {
    byte[] bytes = "bytes".getBytes();
    new MockUnit(Renderer.Context.class)
        .expect(defaultType)
        .expect(unit -> {
          Renderer.Context ctx = unit.get(Renderer.Context.class);
          ctx.send(bytes);
        })
        .run(unit -> {
          BuiltinRenderer.bytes
              .render(bytes, unit.get(Renderer.Context.class));
        });
  }

  @Test
  public void renderIgnoredAnyOtherArray() throws Exception {
    int[] bytes = new int[0];
    new MockUnit(Renderer.Context.class)
        .run(unit -> {
          BuiltinRenderer.bytes
              .render(bytes, unit.get(Renderer.Context.class));
        });
  }

  @Test
  public void renderIgnore() throws Exception {
    new MockUnit(Renderer.Context.class)
        .run(unit -> {
          BuiltinRenderer.bytes
              .render(new Object(), unit.get(Renderer.Context.class));
        });
  }

  @Test(expected = IOException.class)
  public void renderWithFailure() throws Exception {
    byte[] bytes = "bytes".getBytes();
    new MockUnit(Renderer.Context.class, InputStream.class, OutputStream.class)
        .expect(defaultType)
        .expect(unit -> {
          Renderer.Context ctx = unit.get(Renderer.Context.class);
          ctx.send(bytes);
          expectLastCall().andThrow(new IOException("intentational err"));

        })
        .run(unit -> {
          BuiltinRenderer.bytes
              .render(bytes, unit.get(Renderer.Context.class));
        });
  }

}
