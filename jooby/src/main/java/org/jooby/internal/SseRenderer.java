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

import com.google.common.io.ByteSource;
import org.jooby.MediaType;
import org.jooby.Renderer;
import org.jooby.Sse;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class SseRenderer extends AbstractRendererContext {

  static final ByteSource ID = bytes("id:");
  static final ByteSource EVENT = bytes("event:");
  static final ByteSource RETRY = bytes("retry:");
  static final ByteSource DATA = bytes("data:");
  static final ByteSource COMMENT = bytes(":");
  static final byte nl = '\n';
  static final ByteSource NL = bytes("\n");

  private ByteSource data;

  public SseRenderer(final List<Renderer> renderers, final List<MediaType> produces,
    final Charset charset, Locale locale, final Map<String, Object> locals) {
    super(renderers, produces, charset, locale, locals);
  }

  public byte[] format(final Sse.Event event) throws Exception {
    // comment?
    data = event.comment()
      .map(comment -> ByteSource.concat(COMMENT, bytes(comment), NL))
      .orElse(ByteSource.empty());

    // id?
    data = event.id()
      .map(id -> ByteSource.concat(data, ID, bytes(id.toString()), NL))
      .orElse(data);

    // event?
    data = event.name()
      .map(name -> ByteSource.concat(data, EVENT, bytes(name), NL))
      .orElse(data);

    // retry?
    data = event.retry()
      .map(retry -> ByteSource.concat(data, RETRY, bytes(Long.toString(retry)), NL))
      .orElse(data);

    Optional<Object> value = event.data();
    if (value.isPresent()) {
      render(value.get());
    }

    data = ByteSource.concat(data, NL);

    byte[] bytes = data.read();
    data = null;
    return bytes;
  }

  @Override
  protected void _send(final byte[] bytes) throws Exception {
    List<Integer[]> lines = split(bytes);
    if (lines.size() == 1) {
      data = ByteSource.concat(data, DATA, ByteSource.wrap(bytes), NL);
    } else {
      for (Integer[] line : lines) {
        data = ByteSource.concat(data, DATA, ByteSource.wrap(bytes)
          .slice(line[0], line[1] - line[0]), NL);
      }
    }
  }

  @Override
  protected void _send(final ByteBuffer buffer) throws Exception {
    byte[] bytes;
    if (buffer.hasArray()) {
      _send(buffer.array());
    } else {
      bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      _send(bytes);
    }
  }

  @Override
  protected void _send(final FileChannel file) throws Exception {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void _send(final InputStream stream) throws Exception {
    throw new UnsupportedOperationException();
  }

  private static ByteSource bytes(final String value) {
    return ByteSource.wrap(value.getBytes(StandardCharsets.UTF_8));
  }

  private static List<Integer[]> split(final byte[] bytes) {
    List<Integer[]> range = new ArrayList<>();

    Function<Integer, Integer> nextLine = start -> {
      for (int i = start; i < bytes.length; i++) {
        if (bytes[i] == nl) {
          return i;
        }
      }
      return bytes.length;
    };

    int from = 0;
    int to = nextLine.apply(from);
    int len = bytes.length;
    range.add(new Integer[]{from, to});
    while (to != len) {
      from = to + 1;
      to = nextLine.apply(from);
      if (to > from) {
        range.add(new Integer[]{from, to});
      }
    }
    return range;
  }

}
