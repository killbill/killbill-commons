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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.jooby.MediaType;
import org.jooby.Renderer;
import org.jooby.WebSocket;
import org.jooby.spi.NativeWebSocket;
import org.jooby.test.MockUnit;
import org.junit.Test;

import com.google.common.collect.Lists;

public class WebSocketRendererContextTest {

  @Test(expected = UnsupportedOperationException.class)
  public void fileChannel() throws Exception {
    MediaType produces = MediaType.json;
    new MockUnit(Renderer.class, NativeWebSocket.class, WebSocket.SuccessCallback.class,
        WebSocket.OnError.class)
        .run(unit -> {
          WebSocketRendererContext ctx = new WebSocketRendererContext(
              Lists.newArrayList(unit.get(Renderer.class)),
              unit.get(NativeWebSocket.class),
              produces,
              StandardCharsets.UTF_8,
              Locale.US,
              unit.get(WebSocket.SuccessCallback.class),
              unit.get(WebSocket.OnError.class));
          ctx.send(newFileChannel());
        });
  }

  @Test(expected = UnsupportedOperationException.class)
  public void inputStream() throws Exception {
    MediaType produces = MediaType.json;
    new MockUnit(Renderer.class, NativeWebSocket.class, WebSocket.SuccessCallback.class,
        WebSocket.OnError.class, InputStream.class)
        .run(unit -> {
          WebSocketRendererContext ctx = new WebSocketRendererContext(
              Lists.newArrayList(unit.get(Renderer.class)),
              unit.get(NativeWebSocket.class),
              produces,
              StandardCharsets.UTF_8,
              Locale.US,
              unit.get(WebSocket.SuccessCallback.class),
              unit.get(WebSocket.OnError.class));
          ctx.send(unit.get(InputStream.class));
        });
  }

  private FileChannel newFileChannel() {
    return new FileChannel() {
      @Override
      public int read(final ByteBuffer dst) throws IOException {
        return 0;
      }

      @Override
      public long read(final ByteBuffer[] dsts, final int offset, final int length)
          throws IOException {
        return 0;
      }

      @Override
      public int write(final ByteBuffer src) throws IOException {
        return 0;
      }

      @Override
      public long write(final ByteBuffer[] srcs, final int offset, final int length)
          throws IOException {
        return 0;
      }

      @Override
      public long position() throws IOException {
        return 0;
      }

      @Override
      public FileChannel position(final long newPosition) throws IOException {
        return null;
      }

      @Override
      public long size() throws IOException {
        return 0;
      }

      @Override
      public FileChannel truncate(final long size) throws IOException {
        return null;
      }

      @Override
      public void force(final boolean metaData) throws IOException {
      }

      @Override
      public long transferTo(final long position, final long count, final WritableByteChannel target)
          throws IOException {
        return 0;
      }

      @Override
      public long transferFrom(final ReadableByteChannel src, final long position, final long count)
          throws IOException {
        return 0;
      }

      @Override
      public int read(final ByteBuffer dst, final long position) throws IOException {
        return 0;
      }

      @Override
      public int write(final ByteBuffer src, final long position) throws IOException {
        return 0;
      }

      @Override
      public MappedByteBuffer map(final MapMode mode, final long position, final long size)
          throws IOException {
        return null;
      }

      @Override
      public FileLock lock(final long position, final long size, final boolean shared)
          throws IOException {
        return null;
      }

      @Override
      public FileLock tryLock(final long position, final long size, final boolean shared)
          throws IOException {
        return null;
      }

      @Override
      protected void implCloseChannel() throws IOException {
      }

    };
  }

}
