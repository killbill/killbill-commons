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

import com.google.common.collect.ImmutableList;
import com.google.inject.Injector;
import com.google.inject.Key;

import static java.util.Objects.requireNonNull;

import org.jooby.Err;
import org.jooby.MediaType;
import org.jooby.Mutant;
import org.jooby.Renderer;
import org.jooby.Request;
import org.jooby.WebSocket;
import org.jooby.funzy.Throwing;
import org.jooby.funzy.Try;
import org.jooby.internal.parser.ParserExecutor;
import org.jooby.spi.NativeWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.EOFException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

@SuppressWarnings("unchecked")
public class WebSocketImpl implements WebSocket {

  @SuppressWarnings({"rawtypes"})
  private static final OnMessage NOOP = arg -> {
  };

  private static final OnClose CLOSE_NOOP = arg -> {
  };

  private static final Predicate<Throwable> RESET_BY_PEER = ConnectionResetByPeer::test;

  private static final Predicate<Throwable> SILENT = RESET_BY_PEER
      .or(ClosedChannelException.class::isInstance)
      .or(EOFException.class::isInstance);

  /** The logging system. */
  private final Logger log = LoggerFactory.getLogger(WebSocket.class);

  /** All connected websocket. */
  private static final ConcurrentMap<String, List<WebSocket>> sessions = new ConcurrentHashMap<>();

  private Locale locale;

  private String path;

  private String pattern;

  private Map<Object, String> vars;

  private MediaType consumes;

  private MediaType produces;

  private OnOpen handler;

  private OnMessage<Mutant> messageCallback = NOOP;

  private OnClose closeCallback = CLOSE_NOOP;

  private OnError exceptionCallback = cause -> {
    log.error("execution of WS" + path() + " resulted in exception", cause);
  };

  private NativeWebSocket ws;

  private Injector injector;

  private boolean suspended;

  private List<Renderer> renderers;

  private volatile boolean open;

  private ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();

  public WebSocketImpl(final OnOpen handler, final String path,
      final String pattern, final Map<Object, String> vars,
      final MediaType consumes, final MediaType produces) {
    this.handler = handler;
    this.path = path;
    this.pattern = pattern;
    this.vars = vars;
    this.consumes = consumes;
    this.produces = produces;
  }

  @Override
  public void close(final CloseStatus status) {
    removeSession(this);
    synchronized (this) {
      open = false;
      if (ws != null) {
        ws.close(status.code(), status.reason());
      }
    }
  }

  @Override
  public void resume() {
    addSession(this);
    synchronized (this) {
      if (suspended) {
        ws.resume();
        suspended = false;
      }
    }
  }

  @Override
  public void pause() {
    removeSession(this);
    synchronized (this) {
      if (!suspended) {
        ws.pause();
        suspended = true;
      }
    }
  }

  @Override
  public void terminate() throws Exception {
    removeSession(this);
    synchronized (this) {
      open = false;
      ws.terminate();
    }
  }

  @Override
  public boolean isOpen() {
    return open && ws.isOpen();
  }

  @Override
  public void broadcast(final Object data, final SuccessCallback success, final OnError err)
      throws Exception {
    for (WebSocket ws : sessions.getOrDefault(this.pattern, Collections.emptyList())) {
      try {
        ws.send(data, success, err);
      } catch (Exception ex) {
        err.onError(ex);
      }
    }
  }

  @Override
  public void send(final Object data, final SuccessCallback success, final OnError err)
      throws Exception {
    requireNonNull(data, "Message required.");
    requireNonNull(success, "Success callback required.");
    requireNonNull(err, "Error callback required.");

    synchronized (this) {
      if (isOpen()) {
        new WebSocketRendererContext(
            renderers,
            ws,
            produces,
            StandardCharsets.UTF_8,
            locale,
            success,
            err).render(data);
      } else {
        throw new Err(WebSocket.NORMAL, "WebSocket is closed.");
      }
    }
  }

  @Override
  public void onMessage(final OnMessage<Mutant> callback) throws Exception {
    this.messageCallback = requireNonNull(callback, "Message callback required.");
  }

  public void connect(final Injector injector, final Request req, final NativeWebSocket ws) {
    this.open = true;
    this.injector = requireNonNull(injector, "Injector required.");
    this.ws = requireNonNull(ws, "WebSocket is required.");
    this.locale = req.locale();
    renderers = ImmutableList.copyOf(injector.getInstance(Renderer.KEY));

    /**
     * Bind callbacks
     */
    ws.onBinaryMessage(buffer -> Try
        .run(sync(() -> messageCallback.onMessage(new WsBinaryMessage(buffer))))
        .onFailure(this::handleErr));

    ws.onTextMessage(message -> Try
        .run(sync(() -> messageCallback.onMessage(
            new MutantImpl(injector.getInstance(ParserExecutor.class), consumes,
                new StrParamReferenceImpl("body", "message", ImmutableList.of(message))))))
        .onFailure(this::handleErr));

    ws.onCloseMessage((code, reason) -> {
      removeSession(this);

      Try.run(sync(() -> {
        this.open = false;
        if (closeCallback != null) {
          closeCallback.onClose(reason.map(r -> WebSocket.CloseStatus.of(code, r))
              .orElse(WebSocket.CloseStatus.of(code)));
        }
        closeCallback = null;
      })).onFailure(this::handleErr);
    });

    ws.onErrorMessage(this::handleErr);

    // connect now
    try {
      addSession(this);
      handler.onOpen(req, this);
    } catch (Throwable ex) {
      handleErr(ex);
    }
  }

  @Override
  public String path() {
    return path;
  }

  @Override
  public String pattern() {
    return pattern;
  }

  @Override
  public Map<Object, String> vars() {
    return vars;
  }

  @Override
  public MediaType consumes() {
    return consumes;
  }

  @Override
  public MediaType produces() {
    return produces;
  }

  @Override
  public <T> T require(final Key<T> key) {
    return injector.getInstance(key);
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("WS ").append(path()).append("\n");
    buffer.append("  pattern: ").append(pattern()).append("\n");
    buffer.append("  vars: ").append(vars()).append("\n");
    buffer.append("  consumes: ").append(consumes()).append("\n");
    buffer.append("  produces: ").append(produces()).append("\n");
    return buffer.toString();
  }

  @Override
  public void onError(final WebSocket.OnError callback) {
    this.exceptionCallback = requireNonNull(callback, "A callback is required.");
  }

  @Override
  public void onClose(final WebSocket.OnClose callback) throws Exception {
    this.closeCallback = requireNonNull(callback, "A callback is required.");
  }

  @Override public <T> T get(String name) {
    return (T) ifGet(name).orElseThrow(() -> new NullPointerException(name));
  }

  @Override public <T> Optional<T> ifGet(String name) {
    return Optional.ofNullable((T) attributes.get(name));
  }

  @Nullable @Override public WebSocket set(String name, Object value) {
    attributes.put(name, value);
    return this;
  }

  @Override public <T> Optional<T> unset(String name) {
    return Optional.ofNullable((T) attributes.remove(name));
  }

  @Override public WebSocket unset() {
    attributes.clear();
    return this;
  }

  @Override public Map<String, Object> attributes() {
    return Collections.unmodifiableMap(attributes);
  }

  private void handleErr(final Throwable cause) {
    Try.run(() -> {
      if (SILENT.test(cause)) {
        log.debug("execution of WS" + path() + " resulted in exception", cause);
      } else {
        exceptionCallback.onError(cause);
      }
    })
        .onComplete(() -> cleanup(cause))
        .throwException();
  }

  private void cleanup(final Throwable cause) {
    open = false;
    NativeWebSocket lws = ws;
    this.ws = null;
    this.injector = null;
    this.handler = null;
    this.closeCallback = null;
    this.exceptionCallback = null;
    this.messageCallback = null;

    if (lws != null && lws.isOpen()) {
      WebSocket.CloseStatus closeStatus = WebSocket.SERVER_ERROR;
      if (cause instanceof IllegalArgumentException) {
        closeStatus = WebSocket.BAD_DATA;
      } else if (cause instanceof NoSuchElementException) {
        closeStatus = WebSocket.BAD_DATA;
      } else if (cause instanceof Err) {
        Err err = (Err) cause;
        if (err.statusCode() == 400) {
          closeStatus = WebSocket.BAD_DATA;
        }
      }
      lws.close(closeStatus.code(), closeStatus.reason());
    }
  }

  private Throwing.Runnable sync(final Throwing.Runnable task) {
    return () -> {
      synchronized (this) {
        task.run();
      }
    };
  }

  private static void addSession(WebSocketImpl ws) {
    sessions.computeIfAbsent(ws.pattern, k -> new CopyOnWriteArrayList<>()).add(ws);
  }

  private static void removeSession(WebSocketImpl ws) {
    Optional.ofNullable(sessions.get(ws.pattern)).ifPresent(list -> list.remove(ws));
  }
}
