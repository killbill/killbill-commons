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

import com.google.common.io.ByteStreams;
import org.jooby.Err;
import org.jooby.MediaType;
import org.jooby.Renderer;
import org.jooby.Renderer.Context;
import org.jooby.Status;
import org.jooby.spi.NativeResponse;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class HttpRendererContext extends AbstractRendererContext {

  private Consumer<Long> length;

  private Consumer<MediaType> type;

  private NativeResponse rsp;

  private Optional<String> byteRange;

  public HttpRendererContext(final List<Renderer> renderers,
    final NativeResponse rsp, final Consumer<Long> len, final Consumer<MediaType> type,
    final Map<String, Object> locals, final List<MediaType> produces, final Charset charset,
    final Locale locale, final Optional<String> byteRange) {
    super(renderers, produces, charset, locale, locals);
    this.byteRange = byteRange;
    this.rsp = rsp;
    this.length = len;
    this.type = type;
  }

  @Override
  public Context length(final long length) {
    this.length.accept(length);
    return this;
  }

  @Override
  public Context type(final MediaType type) {
    this.type.accept(type);
    return this;
  }

  @Override
  protected void _send(final ByteBuffer buffer) throws Exception {
    rsp.send(buffer);
  }

  @Override
  protected void _send(final byte[] bytes) throws Exception {
    rsp.send(bytes);
  }

  @Override
  protected void _send(final FileChannel file) throws Exception {
    long[] byteRange = byteRange();
    if (byteRange == null) {
      rsp.send(file);
    } else {
      rsp.send(file, byteRange[0], byteRange[1]);
    }
  }

  @Override
  protected void _send(final InputStream stream) throws Exception {
    long[] byteRange = byteRange();
    if (byteRange == null) {
      rsp.send(stream);
    } else {
      stream.skip(byteRange[0]);
      rsp.send(ByteStreams.limit(stream, byteRange[1]));
    }
  }

  private long[] byteRange() {
    long len = rsp.header("Content-Length").map(Long::parseLong).orElse(-1L);
    if (len > 0) {
      if (byteRange.isPresent()) {
        String raw = byteRange.get();
        long[] range = ByteRange.parse(raw);
        long start = range[0];
        long end = range[1];
        if (start == -1) {
          start = len - end;
          end = len - 1;
        }
        if (end == -1 || end > len - 1) {
          end = len - 1;
        }
        if (start > end) {
          throw new Err(Status.REQUESTED_RANGE_NOT_SATISFIABLE, raw);
        }
        // offset
        long limit = (end - start + 1);
        rsp.header("Accept-Ranges", "bytes");
        rsp.header("Content-Range", "bytes " + start + "-" + end + "/" + len);
        rsp.header("Content-Length", Long.toString(limit));
        rsp.statusCode(Status.PARTIAL_CONTENT.value());
        return new long[]{start, limit};
      }
    }
    return null;
  }

}
