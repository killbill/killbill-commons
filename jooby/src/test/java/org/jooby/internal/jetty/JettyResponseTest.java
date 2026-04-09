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
package org.jooby.internal.jetty;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isA;
import static org.junit.Assert.assertArrayEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;

import org.eclipse.jetty.server.HttpOutput;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.jooby.servlet.ServletServletRequest;
import org.jooby.test.MockUnit;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JettyResponseTest {

  private MockUnit.Block servletRequest = unit -> {
    Request req = unit.get(Request.class);
    ServletServletRequest request = unit.get(ServletServletRequest.class);
    when(request.servletRequest()).thenReturn(req);
  };

  private MockUnit.Block startAsync = unit -> {
    ServletServletRequest request = unit.get(ServletServletRequest.class);
    HttpServletRequest req = unit.mock(HttpServletRequest.class);
    when(req.isAsyncStarted()).thenReturn(false);
    when(req.startAsync()).thenReturn(unit.get(AsyncContext.class));
    when(request.servletRequest()).thenReturn(req);
  };

  private MockUnit.Block asyncStarted = unit -> {
    Request request = unit.get(Request.class);
    when(request.isAsyncStarted()).thenReturn(true);
  };

  private MockUnit.Block noAsyncStarted = unit -> {
    Request request = unit.get(Request.class);
    when(request.isAsyncStarted()).thenReturn(false);
  };

  @Test
  public void defaults() throws Exception {
    new MockUnit(ServletServletRequest.class, Request.class, Response.class)
        .expect(servletRequest)
        .run(unit -> {
          new JettyResponse(unit.get(ServletServletRequest.class), unit.get(Response.class));
        });
  }

  @Test
  public void sendBytes() throws Exception {
    byte[] bytes = "bytes".getBytes();
    java.util.concurrent.atomic.AtomicReference<ByteBuffer> capturedBuf = new java.util.concurrent.atomic.AtomicReference<>();
    new MockUnit(ServletServletRequest.class, Request.class, Response.class, HttpOutput.class)
        .expect(servletRequest)
        .expect(unit -> {
          HttpOutput output = unit.get(HttpOutput.class);
          doAnswer(inv -> { capturedBuf.set(inv.getArgument(0)); return null; })
              .when(output).sendContent(any(ByteBuffer.class));

          Response rsp = unit.get(Response.class);
          rsp.setHeader("Transfer-Encoding", null);
          when(rsp.getHttpOutput()).thenReturn(output);
        })
        .run(unit -> {
          new JettyResponse(unit.get(ServletServletRequest.class), unit.get(Response.class))
              .send(bytes);
        }, unit -> {
          assertArrayEquals(bytes, capturedBuf.get().array());
        });
  }

  @Test
  public void sendBuffer() throws Exception {
    byte[] bytes = "bytes".getBytes();
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    new MockUnit(ServletServletRequest.class, Request.class, Response.class, HttpOutput.class)
        .expect(servletRequest)
        .expect(unit -> {
          HttpOutput output = unit.get(HttpOutput.class);
          output.sendContent(eq(buffer));

          Response rsp = unit.get(Response.class);
          when(rsp.getHttpOutput()).thenReturn(output);
        })
        .run(unit -> {
          new JettyResponse(unit.get(ServletServletRequest.class), unit.get(Response.class))
              .send(buffer);
        });
  }

  @Test
  public void sendInputStream() throws Exception {
    new MockUnit(ServletServletRequest.class, Request.class, Response.class, HttpOutput.class,
        InputStream.class, AsyncContext.class)
            .expect(servletRequest)
            .expect(unit -> {
              ReadableByteChannel channel = unit.mock(ReadableByteChannel.class);
              unit.mockStatic(Channels.class).when(() -> Channels.newChannel(unit.get(InputStream.class))).thenReturn(channel);

              HttpOutput output = unit.get(HttpOutput.class);
              output.sendContent(eq(channel), isA(JettyResponse.class));

              Response rsp = unit.get(Response.class);
              when(rsp.getHttpOutput()).thenReturn(output);
            })
            .expect(startAsync)
            .run(unit -> {
              new JettyResponse(unit.get(ServletServletRequest.class), unit.get(Response.class))
                  .send(unit.get(InputStream.class));
            });
  }

  @Test
  public void sendInputStreamAsyncStarted() throws Exception {
    new MockUnit(ServletServletRequest.class, Request.class, Response.class, HttpOutput.class,
        InputStream.class, AsyncContext.class)
            .expect(servletRequest)
            .expect(unit -> {
              ReadableByteChannel channel = unit.mock(ReadableByteChannel.class);
              unit.mockStatic(Channels.class).when(() -> Channels.newChannel(unit.get(InputStream.class))).thenReturn(channel);

              HttpOutput output = unit.get(HttpOutput.class);
              output.sendContent(eq(channel), isA(JettyResponse.class));

              Response rsp = unit.get(Response.class);
              when(rsp.getHttpOutput()).thenReturn(output);
            })
            .expect(unit -> {
              ServletServletRequest request = unit.get(ServletServletRequest.class);
              HttpServletRequest req = unit.mock(HttpServletRequest.class);
              when(req.isAsyncStarted()).thenReturn(true);
              when(request.servletRequest()).thenReturn(req);
            })
            .run(unit -> {
              JettyResponse rsp = new JettyResponse(unit.get(ServletServletRequest.class),
                  unit.get(Response.class));
              rsp.send(unit.get(InputStream.class));
              rsp.end();
            });
  }

  @Test
  public void sendSmallFileChannel() throws Exception {
    FileChannel channel = newFileChannel(1);
    new MockUnit(ServletServletRequest.class, Request.class, Response.class, HttpOutput.class,
        AsyncContext.class)
            .expect(servletRequest)
            .expect(unit -> {
              HttpOutput output = unit.get(HttpOutput.class);
              output.sendContent(eq(channel));

              Response rsp = unit.get(Response.class);
              when(rsp.getBufferSize()).thenReturn(2);
              when(rsp.getHttpOutput()).thenReturn(output);
            })
            .run(unit -> {
              new JettyResponse(unit.get(ServletServletRequest.class), unit.get(Response.class))
                  .send(channel);
            });
  }

  @Test
  public void sendLargeFileChannel() throws Exception {
    FileChannel channel = newFileChannel(10);
    new MockUnit(ServletServletRequest.class, Request.class, Response.class, HttpOutput.class,
        AsyncContext.class)
            .expect(servletRequest)
            .expect(unit -> {
              HttpOutput output = unit.get(HttpOutput.class);
              output.sendContent(eq(channel), isA(JettyResponse.class));

              Response rsp = unit.get(Response.class);
              when(rsp.getBufferSize()).thenReturn(5);
              when(rsp.getHttpOutput()).thenReturn(output);
            })
            .expect(startAsync)
            .run(unit -> {
              new JettyResponse(unit.get(ServletServletRequest.class), unit.get(Response.class))
                  .send(channel);
            });
  }

  @Test
  public void succeeded() throws Exception {
    byte[] bytes = "bytes".getBytes();
    new MockUnit(ServletServletRequest.class, Request.class, Response.class, HttpOutput.class,
        AsyncContext.class)
            .expect(servletRequest)
            .expect(unit -> {
              HttpOutput output = unit.get(HttpOutput.class);
              output.sendContent(any(ByteBuffer.class));
              output.close();

              Response rsp = unit.get(Response.class);
              rsp.setHeader("Transfer-Encoding", null);
              when(rsp.getHttpOutput()).thenReturn(output);
            })
            .expect(noAsyncStarted)
            .run(unit -> {
              JettyResponse rsp = new JettyResponse(unit.get(ServletServletRequest.class),
                  unit.get(Response.class));
              rsp.send(bytes);
              rsp.succeeded();
            });
  }

  @Test
  public void succeededAsync() throws Exception {
    FileChannel channel = newFileChannel(10);
    new MockUnit(ServletServletRequest.class, Request.class, Response.class, HttpOutput.class,
        AsyncContext.class)
            .expect(servletRequest)
            .expect(unit -> {
              HttpOutput output = unit.get(HttpOutput.class);
              output.sendContent(eq(channel), isA(JettyResponse.class));

              Response rsp = unit.get(Response.class);
              when(rsp.getBufferSize()).thenReturn(5);
              when(rsp.getHttpOutput()).thenReturn(output);
            })
            .expect(startAsync)
            .expect(asyncStarted)
            .expect(unit -> {
              Request req = unit.get(Request.class);

              AsyncContext ctx = unit.get(AsyncContext.class);
              ctx.complete();

              when(req.getAsyncContext()).thenReturn(ctx);
            })
            .run(unit -> {
              JettyResponse rsp = new JettyResponse(unit.get(ServletServletRequest.class),
                  unit.get(Response.class));
              rsp.send(channel);
              rsp.succeeded();
            });
  }

  @Test
  public void end() throws Exception {
    new MockUnit(ServletServletRequest.class, Request.class, Response.class, HttpOutput.class)
        .expect(servletRequest)
        .expect(unit -> {
          HttpOutput output = unit.get(HttpOutput.class);
          output.close();

          Response rsp = unit.get(Response.class);
          when(rsp.getHttpOutput()).thenReturn(output);
        })
        .expect(noAsyncStarted)
        .run(unit -> {
          new JettyResponse(unit.get(ServletServletRequest.class), unit.get(Response.class))
              .end();
        });
  }

  @Test
  public void failed() throws Exception {
    IOException cause = new IOException();
    new MockUnit(ServletServletRequest.class, Request.class, Response.class, HttpOutput.class)
        .expect(servletRequest)
        .expect(unit -> {
          Logger log = unit.mock(Logger.class);
          log.error("execution of /path resulted in exception", cause);

          unit.mockStatic(LoggerFactory.class).when(() -> LoggerFactory.getLogger(org.jooby.Response.class)).thenReturn(log);
        })
        .expect(unit -> {
          HttpOutput output = unit.get(HttpOutput.class);
          output.close();

          Response rsp = unit.get(Response.class);
          when(rsp.getHttpOutput()).thenReturn(output);
        })
        .expect(noAsyncStarted)
        .expect(unit -> {
          ServletServletRequest req = unit.get(ServletServletRequest.class);
          when(req.path()).thenReturn("/path");
        })
        .run(unit -> {
          new JettyResponse(unit.get(ServletServletRequest.class), unit.get(Response.class))
              .failed(cause);
        });
  }

  private FileChannel newFileChannel(final int size) {
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
        return size;
      }

      @Override
      public FileChannel truncate(final long size) throws IOException {
        return null;
      }

      @Override
      public void force(final boolean metaData) throws IOException {
      }

      @Override
      public long transferTo(final long position, final long count,
          final WritableByteChannel target)
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
