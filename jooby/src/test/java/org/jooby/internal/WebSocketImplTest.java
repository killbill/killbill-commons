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

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.isA;
import org.jooby.Err;
import org.jooby.MediaType;
import org.jooby.Mutant;
import org.jooby.Renderer;
import org.jooby.Request;
import org.jooby.WebSocket;
import org.jooby.WebSocket.CloseStatus;
import org.jooby.WebSocket.OnClose;
import org.jooby.WebSocket.OnMessage;
import org.jooby.internal.parser.ParserExecutor;
import org.jooby.spi.NativeWebSocket;
import org.jooby.test.MockUnit;
import org.jooby.test.MockUnit.Block;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class WebSocketImplTest {

  private Block connect = unit -> {
    WebSocket.OnOpen1 handler = unit.get(WebSocket.OnOpen1.class);
    handler.onOpen(eq(unit.get(Request.class)), isA(WebSocketImpl.class));

    Injector injector = unit.get(Injector.class);

    when(injector.getInstance(Key.get(new TypeLiteral<Set<Renderer>>() {
    }))).thenReturn(Collections.emptySet());

  };

  @SuppressWarnings("unchecked")
  private Block callbacks = unit -> {
    NativeWebSocket nws = unit.get(NativeWebSocket.class);
    nws.onBinaryMessage(isA(Consumer.class));
    nws.onTextMessage(isA(Consumer.class));
    nws.onErrorMessage(isA(Consumer.class));
    nws.onCloseMessage(isA(BiConsumer.class));
  };

  private Block locale = unit -> {
    Request req = unit.get(Request.class);
    when(req.locale()).thenReturn(Locale.CANADA);
  };

  @SuppressWarnings({"resource"})
  @Test
  public void sendString() throws Exception {
    Object data = "String";
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;
    new MockUnit(WebSocket.OnOpen1.class, WebSocket.SuccessCallback.class,
        WebSocket.OnError.class, Injector.class, Request.class, NativeWebSocket.class)
        .expect(connect)
        .expect(callbacks)
        .expect(locale)
        .expect(unit -> {
          List<Renderer> renderers = Collections.emptyList();

          NativeWebSocket ws = unit.get(NativeWebSocket.class);
          when(ws.isOpen()).thenReturn(true);

          WebSocketRendererContext ctx = unit.mockConstructor(WebSocketRendererContext.class,
              new Class[]{List.class, NativeWebSocket.class, MediaType.class, Charset.class,
                  Locale.class,
                  WebSocket.SuccessCallback.class,
                  WebSocket.OnError.class},
              renderers, ws,
              produces, StandardCharsets.UTF_8,
              Locale.CANADA,
              unit.get(WebSocket.SuccessCallback.class),
              unit.get(WebSocket.OnError.class));
          ctx.render(data);
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));

          ws.send(data, unit.get(WebSocket.SuccessCallback.class),
              unit.get(WebSocket.OnError.class));
        });
  }

  @SuppressWarnings("unchecked")
  @Before
  @After
  public void resetSessions() throws Exception {
    Field field = WebSocketImpl.class.getDeclaredField("sessions");
    field.setAccessible(true);
    Map<String, List<WebSocket>> sessions = (Map<String, List<WebSocket>>) field.get(null);
    sessions.clear();
  }

  @SuppressWarnings({"resource"})
  @Test
  public void sendBroadcast() throws Exception {
    Object data = "String";
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;
    new MockUnit(WebSocket.OnOpen1.class, WebSocket.SuccessCallback.class,
        WebSocket.OnError.class, Injector.class, Request.class, NativeWebSocket.class)
        .expect(connect)
        .expect(callbacks)
        .expect(locale)
        .expect(unit -> {
          List<Renderer> renderers = Collections.emptyList();

          NativeWebSocket ws = unit.get(NativeWebSocket.class);
          when(ws.isOpen()).thenReturn(true);

          WebSocketRendererContext ctx = unit.mockConstructor(WebSocketRendererContext.class,
              new Class[]{List.class, NativeWebSocket.class, MediaType.class, Charset.class,
                  Locale.class,
                  WebSocket.SuccessCallback.class,
                  WebSocket.OnError.class},
              renderers, ws,
              produces, StandardCharsets.UTF_8,
              Locale.CANADA,
              unit.get(WebSocket.SuccessCallback.class),
              unit.get(WebSocket.OnError.class));
          ctx.render(data);
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));

          ws.broadcast(data, unit.get(WebSocket.SuccessCallback.class),
              unit.get(WebSocket.OnError.class));
        });
  }

  @SuppressWarnings({"resource"})
  @Test
  public void sendBroadcastErr() throws Exception {
    Object data = "String";
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;
    new MockUnit(WebSocket.OnOpen1.class, WebSocket.SuccessCallback.class,
        WebSocket.OnError.class, Injector.class, Request.class, NativeWebSocket.class)
        .expect(connect)
        .expect(callbacks)
        .expect(locale)
        .expect(unit -> {
          List<Renderer> renderers = Collections.emptyList();

          NativeWebSocket ws = unit.get(NativeWebSocket.class);
          when(ws.isOpen()).thenReturn(true);

          WebSocketRendererContext ctx = unit.mockConstructor(WebSocketRendererContext.class,
              new Class[]{List.class, NativeWebSocket.class, MediaType.class, Charset.class,
                  Locale.class,
                  WebSocket.SuccessCallback.class,
                  WebSocket.OnError.class},
              renderers, ws,
              produces, StandardCharsets.UTF_8,
              Locale.CANADA,
              unit.get(WebSocket.SuccessCallback.class),
              unit.get(WebSocket.OnError.class));
          IllegalStateException x = new IllegalStateException("intentional err");
          doThrow(x).when(ctx).render(data);
          unit.get(WebSocket.OnError.class).onError(x);
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));

          ws.broadcast(data, unit.get(WebSocket.SuccessCallback.class),
              unit.get(WebSocket.OnError.class));
        });
  }

  @SuppressWarnings({"resource"})
  @Test(expected = Err.class)
  public void sendClose() throws Exception {
    Object data = "String";
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;
    new MockUnit(WebSocket.OnOpen1.class, WebSocket.SuccessCallback.class,
        WebSocket.OnError.class, Injector.class, Request.class, NativeWebSocket.class)
        .expect(connect)
        .expect(callbacks)
        .expect(locale)
        .expect(unit -> {
          NativeWebSocket ws = unit.get(NativeWebSocket.class);
          when(ws.isOpen()).thenReturn(false);
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));

          ws.send(data, unit.get(WebSocket.SuccessCallback.class),
              unit.get(WebSocket.OnError.class));
        });
  }

  @SuppressWarnings("resource")
  @Test
  public void toStr() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;

    new MockUnit(WebSocket.OnOpen1.class)
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          assertEquals("WS /\n" +
              "  pattern: /pattern\n" +
              "  vars: {}\n" +
              "  consumes: */*\n" +
              "  produces: */*\n" +
              "", ws.toString());
        });
  }

  @SuppressWarnings("resource")
  @Test
  public void attributes() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;

    new MockUnit(WebSocket.OnOpen1.class)
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          assertEquals(Map.of(), ws.attributes());

          ws.set("foo", "bar");
          assertEquals("bar", ws.get("foo"));
          assertEquals(Optional.empty(), ws.ifGet("bar"));
          assertEquals(Optional.of("bar"), ws.unset("foo"));
          assertEquals(Map.of(), ws.attributes());
          ws.set("foo", "bar");
          ws.unset();
          assertEquals(Map.of(), ws.attributes());

          try {
            ws.get("foo");
            fail();
          } catch (NullPointerException x) {

          }
        });
  }

  @SuppressWarnings({"resource"})
  @Test
  public void isOpen() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;
    new MockUnit(WebSocket.OnOpen1.class, WebSocket.SuccessCallback.class,
        WebSocket.OnError.class, Injector.class, Request.class, NativeWebSocket.class)
        .expect(connect)
        .expect(callbacks)
        .expect(locale)
        .expect(unit -> {
          NativeWebSocket ws = unit.get(NativeWebSocket.class);
          when(ws.isOpen()).thenReturn(true);
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));

          assertTrue(ws.isOpen());
        });
  }

  @SuppressWarnings("resource")
  @Test
  public void pauseAndResume() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;

    new MockUnit(WebSocket.OnOpen1.class, Injector.class, Request.class, NativeWebSocket.class)
        .expect(connect)
        .expect(callbacks)
        .expect(locale)
        .expect(unit -> {
          NativeWebSocket channel = unit.get(NativeWebSocket.class);
          channel.pause();

          channel.resume();
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));
          ws.pause();

          ws.pause();

          ws.resume();

          ws.resume();
        });
  }

  @Test
  public void close() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;

    new MockUnit(WebSocket.OnOpen1.class, Injector.class, Request.class, NativeWebSocket.class)
        .expect(connect)
        .expect(callbacks)
        .expect(locale)
        .expect(unit -> {
          NativeWebSocket ws = unit.get(NativeWebSocket.class);
          ws.close(WebSocket.NORMAL.code(), WebSocket.NORMAL.reason());
        }).run(unit -> {
      WebSocketImpl ws = new WebSocketImpl(
          unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
      ws.connect(unit.get(Injector.class), unit.get(Request.class),
          unit.get(NativeWebSocket.class));
      ws.close(WebSocket.NORMAL);
    });
  }

  @SuppressWarnings("resource")
  @Test
  public void terminate() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;

    new MockUnit(WebSocket.OnOpen1.class, Injector.class, Request.class, NativeWebSocket.class)
        .expect(connect)
        .expect(callbacks)
        .expect(locale)
        .expect(unit -> {
          NativeWebSocket ws = unit.get(NativeWebSocket.class);
          ws.terminate();
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));
          ws.terminate();
        });
  }

  @SuppressWarnings("resource")
  @Test
  public void props() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;

    new MockUnit(WebSocket.OnOpen1.class)
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          assertEquals(pattern, ws.pattern());
          assertEquals(path, ws.path());
          assertEquals(consumes, ws.consumes());
          assertEquals(produces, ws.produces());
        });
  }

  @SuppressWarnings({"resource", "unchecked"})
  @Test
  public void require() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;
    Object instance = new Object();

    new MockUnit(WebSocket.OnOpen1.class, Injector.class, Request.class, NativeWebSocket.class)
        .expect(connect)
        .expect(locale)
        .expect(unit -> {
          NativeWebSocket nws = unit.get(NativeWebSocket.class);
          nws.onBinaryMessage(isA(Consumer.class));
          nws.onTextMessage(isA(Consumer.class));
          nws.onErrorMessage(isA(Consumer.class));
          nws.onCloseMessage(isA(BiConsumer.class));
        })
        .expect(unit -> {
          Injector injector = unit.get(Injector.class);
          when(injector.getInstance(Key.get(Object.class))).thenReturn(instance);
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));
          assertEquals(instance, ws.require(Object.class));
        });
  }

  @SuppressWarnings({"resource", "unchecked"})
  @Test
  public void onMessage() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;

    AtomicReference<Consumer> textCapture = new AtomicReference<>();

    new MockUnit(WebSocket.OnOpen1.class, Injector.class, OnMessage.class, Request.class,
        NativeWebSocket.class,
        Mutant.class)
        .expect(connect)
        .expect(locale)
        .expect(unit -> {
          NativeWebSocket nws = unit.get(NativeWebSocket.class);
          nws.onBinaryMessage(isA(Consumer.class));
          doAnswer(inv -> { textCapture.set(inv.getArgument(0)); return null; })
              .when(nws).onTextMessage(isA(Consumer.class));
          nws.onErrorMessage(isA(Consumer.class));
          nws.onCloseMessage(isA(BiConsumer.class));
        })
        .expect(unit -> {
          OnMessage<Mutant> callback = unit.get(OnMessage.class);
          callback.onMessage(isA(Mutant.class));
        })
        .expect(unit -> {
          Injector injector = unit.get(Injector.class);
          when(injector.getInstance(ParserExecutor.class)).thenReturn(
              unit.mock(ParserExecutor.class));
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));
          ws.onMessage(unit.get(OnMessage.class));
        }, unit -> {
          textCapture.get().accept("something");
        });
  }

  @SuppressWarnings({"resource", "unchecked"})
  @Test
  public void onErr() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;
    Exception ex = new Exception();

    AtomicReference<Consumer> errorCapture = new AtomicReference<>();

    new MockUnit(WebSocket.OnOpen1.class, Injector.class, Request.class, NativeWebSocket.class,
        WebSocket.OnError.class)
        .expect(connect)
        .expect(locale)
        .expect(unit -> {
          NativeWebSocket nws = unit.get(NativeWebSocket.class);
          nws.onBinaryMessage(isA(Consumer.class));
          nws.onTextMessage(isA(Consumer.class));
          doAnswer(inv -> { errorCapture.set(inv.getArgument(0)); return null; })
              .when(nws).onErrorMessage(isA(Consumer.class));
          nws.onCloseMessage(isA(BiConsumer.class));

          when(nws.isOpen()).thenReturn(false);
        })
        .expect(unit -> {
          WebSocket.OnError callback = unit.get(WebSocket.OnError.class);
          callback.onError(ex);
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));
          ws.onError(unit.get(WebSocket.OnError.class));
        }, unit -> {
          errorCapture.get().accept(ex);
        });
  }

  @SuppressWarnings({"resource", "unchecked"})
  @Test
  public void onSilentErr() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;
    Exception ex = new ClosedChannelException();

    AtomicReference<Consumer> errorCapture = new AtomicReference<>();

    new MockUnit(WebSocket.OnOpen1.class, Injector.class, Request.class, NativeWebSocket.class,
        WebSocket.OnError.class)
        .expect(connect)
        .expect(locale)
        .expect(unit -> {
          NativeWebSocket nws = unit.get(NativeWebSocket.class);
          nws.onBinaryMessage(isA(Consumer.class));
          nws.onTextMessage(isA(Consumer.class));
          doAnswer(inv -> { errorCapture.set(inv.getArgument(0)); return null; })
              .when(nws).onErrorMessage(isA(Consumer.class));
          nws.onCloseMessage(isA(BiConsumer.class));

          when(nws.isOpen()).thenReturn(false);
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));
          ws.onError(unit.get(WebSocket.OnError.class));
        }, unit -> {
          errorCapture.get().accept(ex);
        });
  }

  @SuppressWarnings({"resource", "unchecked"})
  @Test
  public void onErrAndWsOpen() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;
    Exception ex = new Exception();

    AtomicReference<Consumer> errorCapture = new AtomicReference<>();

    new MockUnit(WebSocket.OnOpen1.class, Injector.class, Request.class, NativeWebSocket.class,
        WebSocket.OnError.class)
        .expect(connect)
        .expect(locale)
        .expect(unit -> {
          NativeWebSocket nws = unit.get(NativeWebSocket.class);
          nws.onBinaryMessage(isA(Consumer.class));
          nws.onTextMessage(isA(Consumer.class));
          doAnswer(inv -> { errorCapture.set(inv.getArgument(0)); return null; })
              .when(nws).onErrorMessage(isA(Consumer.class));
          nws.onCloseMessage(isA(BiConsumer.class));

          when(nws.isOpen()).thenReturn(true);
          nws.close(1011, "Server error");
        })
        .expect(unit -> {
          WebSocket.OnError callback = unit.get(WebSocket.OnError.class);
          callback.onError(ex);
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));
          ws.onError(unit.get(WebSocket.OnError.class));
        }, unit -> {
          errorCapture.get().accept(ex);
        });
  }

  @SuppressWarnings({"resource", "unchecked"})
  @Test
  public void onClose() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;
    WebSocket.CloseStatus status = WebSocket.NORMAL;

    AtomicReference<BiConsumer> closeCapture = new AtomicReference<>();
    AtomicReference<WebSocket.CloseStatus> statusCapture = new AtomicReference<>();

    new MockUnit(WebSocket.OnOpen1.class, OnMessage.class, OnClose.class, Request.class,
        NativeWebSocket.class, Injector.class)
        .expect(connect)
        .expect(locale)
        .expect(unit -> {
          NativeWebSocket nws = unit.get(NativeWebSocket.class);
          nws.onBinaryMessage(isA(Consumer.class));
          nws.onTextMessage(isA(Consumer.class));
          nws.onErrorMessage(isA(Consumer.class));
          doAnswer(inv -> { closeCapture.set(inv.getArgument(0)); return null; })
              .when(nws).onCloseMessage(isA(BiConsumer.class));
        })
        .expect(unit -> {
          OnClose callback = unit.get(OnClose.class);
          doAnswer(inv -> { statusCapture.set(inv.getArgument(0)); return null; })
              .when(callback).onClose(isA(WebSocket.CloseStatus.class));
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));
          ws.onClose(unit.get(WebSocket.OnClose.class));
        }, unit -> {
          closeCapture.get()
              .accept(status.code(), Optional.of(status.reason()));
        }, unit -> {
          CloseStatus captured = statusCapture.get();
          assertEquals(status.code(), captured.code());
          assertEquals(status.reason(), captured.reason());
        });
  }

  @SuppressWarnings({"resource", "unchecked"})
  @Test
  public void onCloseNullReason() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;
    WebSocket.CloseStatus status = WebSocket.CloseStatus.of(1000);

    AtomicReference<BiConsumer> closeCapture = new AtomicReference<>();
    AtomicReference<WebSocket.CloseStatus> statusCapture = new AtomicReference<>();

    new MockUnit(WebSocket.OnOpen1.class, OnMessage.class, OnClose.class, NativeWebSocket.class,
        Request.class, Injector.class)
        .expect(connect)
        .expect(locale)
        .expect(unit -> {
          NativeWebSocket nws = unit.get(NativeWebSocket.class);
          nws.onBinaryMessage(isA(Consumer.class));
          nws.onTextMessage(isA(Consumer.class));
          nws.onErrorMessage(isA(Consumer.class));
          doAnswer(inv -> { closeCapture.set(inv.getArgument(0)); return null; })
              .when(nws).onCloseMessage(isA(BiConsumer.class));
        })
        .expect(unit -> {
          OnClose callback = unit.get(OnClose.class);
          doAnswer(inv -> { statusCapture.set(inv.getArgument(0)); return null; })
              .when(callback).onClose(isA(WebSocket.CloseStatus.class));
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));
          ws.onClose(unit.get(OnClose.class));
        }, unit -> {
          closeCapture.get()
              .accept(status.code(), Optional.empty());
        }, unit -> {
          CloseStatus captured = statusCapture.get();
          assertEquals(status.code(), captured.code());
          assertEquals(null, captured.reason());
        });
  }

  @SuppressWarnings({"resource", "unchecked"})
  @Test
  public void onCloseEmptyReason() throws Exception {
    String path = "/";
    String pattern = "/pattern";
    Map<Object, String> vars = new HashMap<>();
    MediaType consumes = MediaType.all;
    MediaType produces = MediaType.all;
    WebSocket.CloseStatus status = WebSocket.CloseStatus.of(1000, "");

    AtomicReference<BiConsumer> closeCapture = new AtomicReference<>();
    AtomicReference<WebSocket.CloseStatus> statusCapture = new AtomicReference<>();

    new MockUnit(WebSocket.OnOpen1.class, OnMessage.class, NativeWebSocket.class, Request.class,
        Injector.class, OnClose.class)
        .expect(connect)
        .expect(locale)
        .expect(unit -> {
          NativeWebSocket nws = unit.get(NativeWebSocket.class);
          nws.onBinaryMessage(isA(Consumer.class));
          nws.onTextMessage(isA(Consumer.class));
          nws.onErrorMessage(isA(Consumer.class));
          doAnswer(inv -> { closeCapture.set(inv.getArgument(0)); return null; })
              .when(nws).onCloseMessage(isA(BiConsumer.class));
        })
        .expect(unit -> {
          OnClose callback = unit.get(OnClose.class);
          doAnswer(inv -> { statusCapture.set(inv.getArgument(0)); return null; })
              .when(callback).onClose(isA(WebSocket.CloseStatus.class));
        })
        .run(unit -> {
          WebSocketImpl ws = new WebSocketImpl(
              unit.get(WebSocket.OnOpen1.class), path, pattern, vars, consumes, produces);
          ws.connect(unit.get(Injector.class), unit.get(Request.class),
              unit.get(NativeWebSocket.class));
          ws.onClose(unit.get(OnClose.class));
        }, unit -> {
          closeCapture.get()
              .accept(status.code(), Optional.of(""));
        }, unit -> {
          CloseStatus captured = statusCapture.get();
          assertEquals(status.code(), captured.code());
          assertEquals(null, captured.reason());
        });
  }

}
