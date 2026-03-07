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

import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.jooby.MediaType;
import org.jooby.Renderer;
import org.jooby.test.MockUnit;
import org.jooby.test.MockUnit.Block;
import org.junit.Test;

public class ByteBufferRendererTest {

  private Block defaultType = unit -> {
    Renderer.Context ctx = unit.get(Renderer.Context.class);
    expect(ctx.type(MediaType.octetstream)).andReturn(ctx);
  };

  @Test
  public void renderArray() throws Exception {
    ByteBuffer bytes = ByteBuffer.wrap("bytes".getBytes());
    new MockUnit(Renderer.Context.class, OutputStream.class)
        .expect(defaultType)
        .expect(unit -> {
          Renderer.Context ctx = unit.get(Renderer.Context.class);
          ctx.send(bytes);
        })
        .run(unit -> {
          BuiltinRenderer.byteBuffer
              .render(bytes, unit.get(Renderer.Context.class));
        });
  }

  @Test
  public void renderDirect() throws Exception {
    ByteBuffer bytes = ByteBuffer.allocateDirect(0);
    new MockUnit(Renderer.Context.class, OutputStream.class)
        .expect(defaultType)
        .expect(unit -> {
          Renderer.Context ctx = unit.get(Renderer.Context.class);
          ctx.send(bytes);
        })
        .run(unit -> {
          BuiltinRenderer.byteBuffer
              .render(bytes, unit.get(Renderer.Context.class));
        });
  }

  @Test
  public void renderIgnore() throws Exception {
    new MockUnit(Renderer.Context.class)
        .run(unit -> {
          BuiltinRenderer.byteBuffer
              .render(new Object(), unit.get(Renderer.Context.class));
        });
  }

}
