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
package org.jooby.internal.mvc;

import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import org.jooby.Mutant;
import org.jooby.Request;
import org.jooby.WebSocket;
import org.jooby.WebSocket.CloseStatus;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.function.Predicate;

@SuppressWarnings({"rawtypes", "unchecked"})
public class MvcWebSocket implements WebSocket.Handler<Mutant> {

  private Object handler;

  private TypeLiteral messageType;

  MvcWebSocket(final WebSocket ws, final Class handler) {
    Injector injector = ws.require(Injector.class)
        .createChildInjector(binder -> binder.bind(WebSocket.class).toInstance(ws));
    this.handler = injector.getInstance(handler);
    this.messageType = TypeLiteral.get(messageType(handler));
  }

  public static WebSocket.OnOpen newWebSocket(final Class handler) {
    return (req, ws) -> {
      MvcWebSocket socket = new MvcWebSocket(ws, handler);
      socket.onOpen(req, ws);
      if (socket.isClose()) {
        ws.onClose(socket::onClose);
      }
      if (socket.isError()) {
        ws.onError(socket::onError);
      }
      ws.onMessage(socket::onMessage);
    };
  }

  @Override
  public void onClose(final CloseStatus status) throws Exception {
    if (isClose()) {
      ((WebSocket.OnClose) handler).onClose(status);
    }
  }

  @Override
  public void onMessage(final Mutant data) throws Exception {
    ((WebSocket.OnMessage) handler).onMessage(data.to(messageType));
  }

  @Override
  public void onError(final Throwable err) {
    if (isError()) {
      ((WebSocket.OnError) handler).onError(err);
    }
  }

  @Override
  public void onOpen(final Request req, final WebSocket ws) throws Exception {
    if (handler instanceof WebSocket.OnOpen) {
      ((WebSocket.OnOpen) handler).onOpen(req, ws);
    }
  }

  private boolean isClose() {
    return handler instanceof WebSocket.OnClose;
  }

  private boolean isError() {
    return handler instanceof WebSocket.OnError;
  }

  static Type messageType(final Class handler) {
    return Arrays.asList(handler.getGenericInterfaces())
        .stream()
        .filter(rawTypeIs(WebSocket.Handler.class).or(rawTypeIs(WebSocket.OnMessage.class)))
        .findFirst()
        .filter(ParameterizedType.class::isInstance)
        .map(it -> ((ParameterizedType) it).getActualTypeArguments()[0])
        .orElseThrow(() -> new IllegalArgumentException(
            "Can't extract message type from: " + handler.getName()));
  }

  private static Predicate<Type> rawTypeIs(Class<?> type) {
    return it -> TypeLiteral.get(it).getRawType().isAssignableFrom(type);
  }

}
