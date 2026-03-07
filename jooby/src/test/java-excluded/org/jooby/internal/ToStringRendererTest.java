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

import org.jooby.MediaType;
import org.jooby.Renderer;
import org.jooby.Results;
import org.jooby.test.MockUnit;
import org.jooby.test.MockUnit.Block;
import org.junit.Test;

public class ToStringRendererTest {

  private Block defaultType = unit -> {
    Renderer.Context ctx = unit.get(Renderer.Context.class);
    expect(ctx.type(MediaType.html)).andReturn(ctx);
  };

  @Test
  public void render() throws Exception {
    Object value = new Object() {
      @Override
      public String toString() {
        return "toString";
      }
    };
    new MockUnit(Renderer.Context.class, Object.class)
        .expect(defaultType)
        .expect(unit -> {
          Renderer.Context ctx = unit.get(Renderer.Context.class);
          ctx.send("toString");
        })
        .run(unit -> {
          BuiltinRenderer.text
              .render(value, unit.get(Renderer.Context.class));
        });

  }

  @Test
  public void renderIgnored() throws Exception {
    new MockUnit(Renderer.Context.class)
        .run(unit -> {
          BuiltinRenderer.text
              .render(Results.html("v"), unit.get(Renderer.Context.class));
        });
  }

}
