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

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.SuspendToken;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.jooby.WebSocket;
import org.jooby.WebSocket.OnError;
import org.jooby.WebSocket.SuccessCallback;
import org.jooby.test.MockUnit;
import org.junit.Test;
import org.slf4j.Logger;

import java.util.function.Consumer;

public class JettyWebSocketTest {

  @Test
  public void newObject() throws Exception {
    new MockUnit()
        .run(unit -> {
          new JettyWebSocket();
        });
  }

  @Test
  public void resume() throws Exception {
    new MockUnit()
        .run(unit -> {
          new JettyWebSocket().resume();
        });
  }

  @Test
  public void pause() throws Exception {
    JettyWebSocket ws = new JettyWebSocket();
    new MockUnit(Session.class, Runnable.class, SuspendToken.class)
        .expect(unit -> {
          Runnable connect = unit.get(Runnable.class);
          connect.run();
        })
        .expect(unit -> {
          SuspendToken token = unit.get(SuspendToken.class);
          token.resume();

          Session session = unit.get(Session.class);
          expect(session.suspend()).andReturn(token);
        })
        .run(unit -> {
          ws.onConnect(unit.get(Runnable.class));
          ws.onWebSocketConnect(unit.get(Session.class));
          ws.pause();
          ws.pause();
          ws.resume();;
        });
  }

  @SuppressWarnings({"unchecked", "rawtypes" })
  @Test
  public void onWebSocketError() throws Exception {
    Throwable cause = new Throwable();
    JettyWebSocket ws = new JettyWebSocket();
    new MockUnit(Consumer.class)
        .expect(unit -> {
          Consumer callback = unit.get(Consumer.class);
          ws.onErrorMessage(callback);
          callback.accept(cause);
        })
        .run(unit -> {
          ws.onWebSocketError(cause);
        });
  }

  @Test
  public void successCallback() throws Exception {
    new MockUnit(Consumer.class, Logger.class, WebSocket.SuccessCallback.class,
        WebSocket.OnError.class)
            .expect(unit -> {
              SuccessCallback callback = unit.get(WebSocket.SuccessCallback.class);
              callback.invoke();
            })
            .run(unit -> {
              WriteCallback callback = JettyWebSocket.callback(unit.get(Logger.class),
                  unit.get(WebSocket.SuccessCallback.class), unit.get(WebSocket.OnError.class));
              callback.writeSuccess();
            });
  }

  @Test
  public void successCallbackErr() throws Exception {
    IllegalStateException cause = new IllegalStateException("intentional err");
    new MockUnit(Consumer.class, Logger.class, WebSocket.SuccessCallback.class,
        WebSocket.OnError.class)
            .expect(unit -> {
              SuccessCallback callback = unit.get(WebSocket.SuccessCallback.class);
              callback.invoke();
              expectLastCall().andThrow(cause);

              Logger logger = unit.get(Logger.class);
              logger.error("Error while invoking success callback", cause);
            })
            .run(unit -> {
              WriteCallback callback = JettyWebSocket.callback(unit.get(Logger.class),
                  unit.get(WebSocket.SuccessCallback.class), unit.get(WebSocket.OnError.class));
              callback.writeSuccess();
            });
  }

  @Test
  public void errCallback() throws Exception {
    IllegalStateException cause = new IllegalStateException("intentional err");
    new MockUnit(Consumer.class, Logger.class, WebSocket.SuccessCallback.class,
        WebSocket.OnError.class)
            .expect(unit -> {
              OnError callback = unit.get(WebSocket.OnError.class);
              callback.onError(cause);
            })
            .run(unit -> {
              WriteCallback callback = JettyWebSocket.callback(unit.get(Logger.class),
                  unit.get(WebSocket.SuccessCallback.class), unit.get(WebSocket.OnError.class));
              callback.writeFailed(cause);
            });
  }

  @Test
  public void errCallbackFailure() throws Exception {
    IllegalStateException cause = new IllegalStateException("intentional err");
    new MockUnit(Consumer.class, Logger.class, WebSocket.SuccessCallback.class,
        WebSocket.OnError.class)
            .expect(unit -> {
              OnError callback = unit.get(WebSocket.OnError.class);
              callback.onError(cause);
              expectLastCall().andThrow(cause);

              Logger logger = unit.get(Logger.class);
              logger.error("Error while invoking err callback", cause);
            })
            .run(unit -> {
              WriteCallback callback = JettyWebSocket.callback(unit.get(Logger.class),
                  unit.get(WebSocket.SuccessCallback.class), unit.get(WebSocket.OnError.class));
              callback.writeFailed(cause);
            });
  }
}
