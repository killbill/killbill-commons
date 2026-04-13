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
package org.jooby.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooby.MediaType;
import org.jooby.Renderer;

class JacksonRenderer implements Renderer {

  protected final ObjectMapper mapper;

  protected final MediaType type;

  public JacksonRenderer(final ObjectMapper mapper, final MediaType type) {
    this.mapper = mapper;
    this.type = type;
  }

  @Override
  public void render(final Object value, final Renderer.Context ctx) throws Exception {
    if (ctx.accepts(type) && mapper.canSerialize(value.getClass())) {
      ctx.type(type);
      renderValue(value, ctx);
    }
  }

  protected void renderValue(final Object value, final Renderer.Context ctx) throws Exception {
    // use UTF-8 and get byte version
    final byte[] bytes;

    if (value instanceof JacksonView) {
      final JacksonView viewResponse = (JacksonView) value;

      bytes = mapper
          .writerWithView(viewResponse.view)
          .writeValueAsBytes(viewResponse.data);
    } else {
      bytes = mapper.writeValueAsBytes(value);
    }

    ctx.length(bytes.length)
        .send(bytes);
  }

  @Override
  public String name() {
    return "json";
  }

  @Override
  public String toString() {
    return name();
  }

}
