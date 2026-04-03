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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.jooby.Err;
import org.jooby.MediaType;
import org.jooby.Renderer;
import org.jooby.Results;
import org.jooby.View;
import org.jooby.test.MockUnit;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class AbstractRendererContextTest {

  @Test(expected = Err.class)
  public void norenderer() throws Throwable {
    List<Renderer> renderers = new ArrayList<>();
    List<MediaType> produces = ImmutableList.of(MediaType.json);
    View value = Results.html("view");
    new MockUnit()
        .run(unit -> {
          new AbstractRendererContext(renderers, produces, StandardCharsets.UTF_8, Locale.US,
              Collections.emptyMap()) {

            @Override
            protected void _send(final byte[] bytes) throws Exception {
            }

            @Override
            protected void _send(final ByteBuffer buffer) throws Exception {
            }

            @Override
            protected void _send(final FileChannel file) throws Exception {
            }

            @Override
            protected void _send(final InputStream stream) throws Exception {
            }

          }.render(value);
        });
  }

}
