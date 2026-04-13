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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isA;
import org.jooby.internal.SseRenderer;
import org.jooby.test.MockUnit;
import org.jooby.test.MockUnit.Block;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class SseTest {

  private Block handshake = unit -> {
    Request request = unit.get(Request.class);
    Injector injector = unit.get(Injector.class);
    Route route = unit.get(Route.class);
    Mutant lastEventId = unit.mock(Mutant.class);

    when(route.produces()).thenReturn(MediaType.ALL);

    when(request.require(Injector.class)).thenReturn(injector);
    when(request.route()).thenReturn(route);
    when(request.attributes()).thenReturn(ImmutableMap.of());
    when(request.header("Last-Event-ID")).thenReturn(lastEventId);

    when(injector.getInstance(Renderer.KEY)).thenReturn(Sets.newHashSet());
  };

  private Block locale = unit -> {
    Request req = unit.get(Request.class);
    when(req.locale()).thenReturn(Locale.CANADA);
  };

  @Test
  public void sseId() throws Exception {
    Sse sse = new Sse() {

      @Override
      protected void closeInternal() {
      }

      @Override
      protected CompletableFuture<Optional<Object>> send(final Optional<Object> id,
        final byte[] data) {
        return null;
      }

      @Override
      protected void handshake(final Runnable handler) throws Exception {
      }
    };
    assertNotNull(sse.id());
    UUID.fromString(sse.id());
    sse.close();
  }

  @Test
  public void handshake() throws Exception {
    new MockUnit(Request.class, Injector.class, Runnable.class, Route.class)
      .expect(handshake)
      .expect(locale)
      .expect(unit -> {
        Injector injector = unit.get(Injector.class);
        when(injector.getInstance(Key.get(Object.class))).thenReturn(null);
        when(injector.getInstance(Key.get(TypeLiteral.get(Object.class)))).thenReturn(null);
        when(injector.getInstance(Key.get(Object.class, Names.named("n")))).thenReturn(null);
      })
      .run(unit -> {
        Sse sse = new Sse() {

          @Override
          protected void closeInternal() {
          }

          @Override
          protected CompletableFuture<Optional<Object>> send(final Optional<Object> id,
            final byte[] data) {
            return null;
          }

          @Override
          protected void handshake(final Runnable handler) throws Exception {
          }
        };
        sse.handshake(unit.get(Request.class), unit.get(Runnable.class));
        sse.require(Object.class);
        sse.require(Key.get(Object.class));
        sse.require(TypeLiteral.get(Object.class));
        sse.require("n", Object.class);
        sse.close();
      });
  }

  @Test
  public void ifCloseClosedChannel() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    new MockUnit()
      .run(unit -> {
        Sse sse = new Sse() {

          @Override
          protected void closeInternal() {
            latch.countDown();
          }

          @Override
          protected CompletableFuture<Optional<Object>> send(final Optional<Object> id,
            final byte[] data) {
            return null;
          }

          @Override
          protected void handshake(final Runnable handler) throws Exception {
          }
        };
        sse.onClose(() -> sse.close());
        sse.ifClose(new ClosedChannelException());
        latch.await();
      });
  }

  @Test
  public void ifCloseBrokenPipe() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    new MockUnit()
      .run(unit -> {
        Sse sse = new Sse() {

          @Override
          protected void closeInternal() {
            latch.countDown();
          }

          @Override
          protected CompletableFuture<Optional<Object>> send(final Optional<Object> id,
            final byte[] data) {
            return null;
          }

          @Override
          protected void handshake(final Runnable handler) throws Exception {
          }
        };
        sse.onClose(() -> sse.close());
        sse.ifClose(new IOException("Broken pipe"));
        latch.await();
      });
  }

  @SuppressWarnings("resource")
  @Test
  public void ifCloseErrorOnFireClose() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    new MockUnit()
      .run(unit -> {
        Sse sse = new Sse() {

          @Override
          protected void closeInternal() {
            latch.countDown();
          }

          @Override
          protected CompletableFuture<Optional<Object>> send(final Optional<Object> id,
            final byte[] data) {
            return null;
          }

          @Override
          protected void handshake(final Runnable handler) throws Exception {
          }
        };
        sse.onClose(() -> {
          throw new IllegalStateException("intentional err");
        });
        sse.ifClose(new IOException("Broken pipe"));
        latch.await();
      });
  }

  @Test
  public void ifCloseFailure() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    new MockUnit()
      .run(unit -> {
        Sse sse = new Sse() {

          @Override
          protected void closeInternal() {
            latch.countDown();
          }

          @Override
          protected CompletableFuture<Optional<Object>> send(final Optional<Object> id,
            final byte[] data) {
            return null;
          }

          @Override
          protected void handshake(final Runnable handler) throws Exception {
          }
        };
        sse.onClose(() -> sse.close());
        sse.ifClose(new IOException("Broken pipe"));
        latch.await();
      });
  }

  @Test(expected = IllegalStateException.class)
  public void closeFailure() throws Exception {
    new MockUnit()
      .run(unit -> {
        Sse sse = new Sse() {

          @Override
          protected void closeInternal() {
            throw new IllegalStateException("intentional err");
          }

          @Override
          protected CompletableFuture<Optional<Object>> send(final Optional<Object> id,
            final byte[] data) {
            return null;
          }

          @Override
          protected void handshake(final Runnable handler) throws Exception {
          }
        };
        sse.close();
      });
  }

  @Test
  public void ifCloseIgnoreIO() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    new MockUnit()
      .run(unit -> {
        Sse sse = new Sse() {

          @Override
          protected void closeInternal() {
            latch.countDown();
          }

          @Override
          protected CompletableFuture<Optional<Object>> send(final Optional<Object> id,
            final byte[] data) {
            return null;
          }

          @Override
          protected void handshake(final Runnable handler) throws Exception {
          }
        };
        sse.onClose(() -> sse.close());
        sse.ifClose(new IOException("Ignored"));
        assertEquals(1, latch.getCount());
      });
  }

  @Test
  public void ifCloseIgnoreEx() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    new MockUnit()
      .run(unit -> {
        Sse sse = new Sse() {

          @Override
          protected void closeInternal() {
            latch.countDown();
          }

          @Override
          protected CompletableFuture<Optional<Object>> send(final Optional<Object> id,
            final byte[] data) {
            return null;
          }

          @Override
          protected void handshake(final Runnable handler) throws Exception {
          }
        };
        sse.onClose(() -> sse.close());
        sse.ifClose(new IllegalArgumentException("Ignored"));
        assertEquals(1, latch.getCount());
      });
  }

  @Test
  public void sseHandlerSuccess() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    java.util.concurrent.atomic.AtomicReference<Runnable> capturedRunnable = new java.util.concurrent.atomic.AtomicReference<>();
    java.util.concurrent.atomic.AtomicReference<Deferred> capturedDeferred = new java.util.concurrent.atomic.AtomicReference<>();
    new MockUnit(Request.class, Response.class, Route.Chain.class, Sse.class)
      .expect(unit -> {
        Request req = unit.get(Request.class);
        Sse sse = unit.get(Sse.class);

        doAnswer(inv -> { capturedRunnable.set(inv.getArgument(1)); return null; })
          .when(sse).handshake(eq(unit.get(Request.class)), any(Runnable.class));

        when(req.require(Sse.class)).thenReturn(sse);
        when(req.path()).thenReturn("/sse");
      })
      .expect(unit -> {
        Response rsp = unit.get(Response.class);
        doAnswer(inv -> { capturedDeferred.set(inv.getArgument(0)); return null; })
          .when(rsp).send(any(Deferred.class));
      })
      .run(unit -> {
        Sse.Handler handler = (req, sse) -> {
          latch.countDown();
        };
        handler.handle(unit.get(Request.class), unit.get(Response.class),
          unit.get(Route.Chain.class));
      }, unit -> {
        Deferred deferred = capturedDeferred.get();
        deferred.handler(null, (value, ex) -> {
        });

        capturedRunnable.get().run();

        latch.await();
      });
  }

  @Test
  public void sseHandlerFailure() throws Exception {
    java.util.concurrent.atomic.AtomicReference<Runnable> capturedRunnable = new java.util.concurrent.atomic.AtomicReference<>();
    java.util.concurrent.atomic.AtomicReference<Deferred> capturedDeferred = new java.util.concurrent.atomic.AtomicReference<>();
    new MockUnit(Request.class, Response.class, Sse.class, Route.Chain.class)
      .expect(unit -> {
        Request req = unit.get(Request.class);
        Sse sse = unit.get(Sse.class);

        doAnswer(inv -> { capturedRunnable.set(inv.getArgument(1)); return null; })
          .when(sse).handshake(eq(unit.get(Request.class)), any(Runnable.class));

        when(req.require(Sse.class)).thenReturn(sse);
        when(req.path()).thenReturn("/sse");
      })
      .expect(unit -> {
        Response rsp = unit.get(Response.class);
        doAnswer(inv -> { capturedDeferred.set(inv.getArgument(0)); return null; })
          .when(rsp).send(any(Deferred.class));
      })
      .run(unit -> {
        Sse.Handler handler = (req, sse) -> {
          throw new IllegalStateException("intentional err");
        };
        handler.handle(unit.get(Request.class), unit.get(Response.class),
          unit.get(Route.Chain.class));
      }, unit -> {
        Deferred deferred = capturedDeferred.get();
        deferred.handler(null, (value, ex) -> {
        });

        capturedRunnable.get().run();
      });
  }

  @Test
  public void sseHandlerHandshakeFailure() throws Exception {
    java.util.concurrent.atomic.AtomicReference<Deferred> capturedDeferred = new java.util.concurrent.atomic.AtomicReference<>();
    new MockUnit(Request.class, Response.class, Sse.class, Route.Chain.class)
      .expect(unit -> {
        Request req = unit.get(Request.class);
        Sse sse = unit.get(Sse.class);

        doThrow(new IllegalStateException("intentional error")).when(sse).handshake(eq(unit.get(Request.class)), any(Runnable.class));

        when(req.require(Sse.class)).thenReturn(sse);
        when(req.path()).thenReturn("/sse");
      })
      .expect(unit -> {
        Response rsp = unit.get(Response.class);
        doAnswer(inv -> { capturedDeferred.set(inv.getArgument(0)); return null; })
          .when(rsp).send(any(Deferred.class));
      })
      .run(unit -> {
        Sse.Handler handler = (req, sse) -> {
        };
        handler.handle(unit.get(Request.class), unit.get(Response.class),
          unit.get(Route.Chain.class));
      }, unit -> {
        Deferred deferred = capturedDeferred.get();
        deferred.handler(null, (value, ex) -> {
        });
      });
  }

  @Test
  public void sseKeepAlive() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    new MockUnit()
      .run(unit -> {
        Sse sse = new Sse() {

          @Override
          protected void closeInternal() {
          }

          @Override
          protected CompletableFuture<Optional<Object>> send(final Optional<Object> id,
            final byte[] data) {
            return CompletableFuture.completedFuture(id);
          }

          @Override
          public Sse keepAlive(final long millis) {
            assertEquals(100, millis);
            latch.countDown();
            return this;
          }

          @Override
          protected void handshake(final Runnable handler) throws Exception {
          }
        };

        new Sse.KeepAlive(sse, 100).run();
        latch.await();
      });
  }

  @SuppressWarnings("resource")
  @Test
  public void renderFailure() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    Object data = new Object();
    new MockUnit(Request.class, Route.class, Injector.class, Runnable.class)
      .expect(handshake)
      .expect(locale)
      .expect(unit -> {
        SseRenderer renderer = unit.constructor(SseRenderer.class)
          .args(List.class, List.class, Charset.class, Locale.class, Map.class)
          .build(isA(List.class), isA(List.class), eq(StandardCharsets.UTF_8),
            eq(Locale.CANADA), isA(Map.class));

        when(renderer.format(isA(Sse.Event.class))).thenThrow(new IOException("failure"));
      })
      .run(unit -> {
        Sse sse = new Sse() {

          @Override
          protected void closeInternal() {
          }

          @Override
          protected void fireCloseEvent() {
          }

          @Override
          protected CompletableFuture<Optional<Object>> send(final Optional<Object> id,
            final byte[] data) {
            CompletableFuture<Optional<Object>> promise = new CompletableFuture<>();
            promise.completeExceptionally(new IOException("intentional err"));
            return promise;
          }

          @Override
          public Sse keepAlive(final long millis) {
            return this;
          }

          @Override
          protected void handshake(final Runnable handler) throws Exception {
          }
        };
        sse.handshake(unit.get(Request.class), unit.get(Runnable.class));
        sse.event(data).type(MediaType.all).send()
          .whenComplete((v, x) -> Optional.ofNullable(x).ifPresent(ex -> latch.countDown()));
        latch.await();
      });
  }

  @Test
  public void sseKeepAliveFailure() throws Exception {
    CountDownLatch latch = new CountDownLatch(2);
    new MockUnit()
      .run(unit -> {
        Sse sse = new Sse() {

          @Override
          protected void closeInternal() {
            latch.countDown();
          }

          @Override
          protected void fireCloseEvent() {
            latch.countDown();
          }

          @Override
          protected CompletableFuture<Optional<Object>> send(final Optional<Object> id,
            final byte[] data) {
            CompletableFuture<Optional<Object>> promise = new CompletableFuture<>();
            promise.completeExceptionally(new IOException("intentional err"));
            return promise;
          }

          @Override
          public Sse keepAlive(final long millis) {
            return this;
          }

          @Override
          protected void handshake(final Runnable handler) throws Exception {
          }
        };

        new Sse.KeepAlive(sse, 100).run();
        latch.await();
      });
  }

}
