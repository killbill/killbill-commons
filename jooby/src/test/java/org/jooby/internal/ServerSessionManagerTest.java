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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.jooby.Cookie;
import org.jooby.Mutant;
import org.jooby.Request;
import org.jooby.Response;
import org.jooby.Session;
import org.jooby.Session.Definition;
import org.jooby.Session.Store;
import org.jooby.internal.parser.ParserExecutor;
import org.jooby.test.MockUnit;
import org.jooby.test.MockUnit.Block;
import org.junit.Test;

import com.typesafe.config.Config;

public class ServerSessionManagerTest {

  private Block noSecret = unit -> {
    Config config = unit.get(Config.class);
    when(config.hasPath("application.secret")).thenReturn(false);
  };

  private Block cookie = unit -> {
    Definition session = unit.get(Session.Definition.class);
    when(session.cookie()).thenReturn(unit.get(Cookie.Definition.class));
  };

  private Block storeGet = unit -> {
    Store store = unit.get(Store.class);
    when(store.get(org.mockito.ArgumentMatchers.any(Session.Builder.class)))
        .thenReturn(unit.get(SessionImpl.class));
  };

  @Test
  public void newServerSessionManager() throws Exception {
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class)
            .expect(noSecret)
            .expect(cookie)
            .expect(saveInterval(-1L))
            .expect(maxAge(-1))
            .run(unit -> {
              new ServerSessionManager(unit.get(Config.class), unit.get(Session.Definition.class),
                  unit.get(Session.Store.class), unit.get(ParserExecutor.class));
            });
  }

  @Test
  public void destroy() throws Exception {
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class, Session.class)
            .expect(noSecret)
            .expect(cookie)
            .expect(saveInterval(-1L))
            .expect(maxAge(-1))
            .expect(unit -> {
              Session session = unit.get(Session.class);
              when(session.id()).thenReturn("sid");

              Store store = unit.get(Session.Store.class);
              store.delete("sid");
            })
            .run(unit -> {
              new ServerSessionManager(unit.get(Config.class), unit.get(Session.Definition.class),
                  unit.get(Session.Store.class), unit.get(ParserExecutor.class))
                      .destroy(unit.get(Session.class));
            });
  }

  @Test
  public void storeCreateSession() throws Exception {
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class, RequestScopedSession.class, SessionImpl.class)
            .expect(noSecret)
            .expect(cookie)
            .expect(saveInterval(-1L))
            .expect(maxAge(-1))
            .expect(reqSession())
            .expect(unit -> {
              SessionImpl session = unit.get(SessionImpl.class);
              session.touch();
              when(session.isNew()).thenReturn(true);
              session.aboutToSave();

              Store store = unit.get(Store.class);
              store.create(session);

              session.markAsSaved();
            })
            .run(unit -> {
              new ServerSessionManager(unit.get(Config.class), unit.get(Session.Definition.class),
                  unit.get(Session.Store.class), unit.get(ParserExecutor.class))
                      .requestDone(unit.get(RequestScopedSession.class));
            });
  }

  @Test
  public void storeDirtySession() throws Exception {
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class, RequestScopedSession.class, SessionImpl.class)
            .expect(noSecret)
            .expect(cookie)
            .expect(saveInterval(-1L))
            .expect(maxAge(-1))
            .expect(reqSession())
            .expect(unit -> {
              SessionImpl session = unit.get(SessionImpl.class);
              session.touch();
              when(session.isNew()).thenReturn(false);
              when(session.isDirty()).thenReturn(true);
              session.aboutToSave();

              Store store = unit.get(Store.class);
              store.save(session);

              session.markAsSaved();
            })
            .run(unit -> {
              new ServerSessionManager(unit.get(Config.class), unit.get(Session.Definition.class),
                  unit.get(Session.Store.class), unit.get(ParserExecutor.class))
                      .requestDone(unit.get(RequestScopedSession.class));
            });
  }

  @Test
  public void storeSaveIntervalSession() throws Exception {
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class, RequestScopedSession.class, SessionImpl.class)
            .expect(noSecret)
            .expect(cookie)
            .expect(saveInterval(-1L))
            .expect(maxAge(-1))
            .expect(reqSession())
            .expect(unit -> {
              SessionImpl session = unit.get(SessionImpl.class);
              session.touch();
              when(session.isNew()).thenReturn(false);
              when(session.isDirty()).thenReturn(false);
              when(session.savedAt()).thenReturn(0L);
              session.aboutToSave();

              Store store = unit.get(Store.class);
              store.save(session);

              session.markAsSaved();
            })
            .run(unit -> {
              new ServerSessionManager(unit.get(Config.class), unit.get(Session.Definition.class),
                  unit.get(Session.Store.class), unit.get(ParserExecutor.class))
                      .requestDone(unit.get(RequestScopedSession.class));
            });
  }

  @Test
  public void storeSkipSaveIntervalSession() throws Exception {
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class, RequestScopedSession.class, SessionImpl.class)
            .expect(noSecret)
            .expect(cookie)
            .expect(saveInterval(-1L))
            .expect(maxAge(-1))
            .expect(reqSession())
            .expect(unit -> {
              SessionImpl session = unit.get(SessionImpl.class);
              session.touch();
              when(session.isNew()).thenReturn(false);
              when(session.isDirty()).thenReturn(false);
              when(session.savedAt()).thenReturn(Long.MAX_VALUE);
              session.markAsSaved();
            })
            .run(unit -> {
              new ServerSessionManager(unit.get(Config.class), unit.get(Session.Definition.class),
                  unit.get(Session.Store.class), unit.get(ParserExecutor.class))
                      .requestDone(unit.get(RequestScopedSession.class));
            });
  }

  @Test
  public void storeFailure() throws Exception {
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class, RequestScopedSession.class, SessionImpl.class)
            .expect(noSecret)
            .expect(cookie)
            .expect(saveInterval(-1L))
            .expect(maxAge(-1))
            .expect(reqSession())
            .expect(unit -> {
              SessionImpl session = unit.get(SessionImpl.class);
              session.touch();
              when(session.isNew()).thenReturn(true);
              session.aboutToSave();
              Store store = unit.get(Store.class);
              doThrow(new IllegalStateException("intentional err")).when(store).create(session);
            })
            .run(unit -> {
              new ServerSessionManager(unit.get(Config.class), unit.get(Session.Definition.class),
                  unit.get(Session.Store.class), unit.get(ParserExecutor.class))
                      .requestDone(unit.get(RequestScopedSession.class));
            });
  }

  private Block reqSession() {
    return unit -> {
      RequestScopedSession req = unit.get(RequestScopedSession.class);
      when(req.session()).thenReturn(unit.get(SessionImpl.class));
    };
  }

  @Test
  public void noSession() throws Exception {
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class, Request.class, Response.class)
            .expect(noSecret)
            .expect(cookie)
            .expect(saveInterval(-1L))
            .expect(maxAge(-1))
            .expect(unit -> {
              Cookie.Definition cookie = unit.get(Cookie.Definition.class);
              when(cookie.name()).thenReturn(Optional.of("sid"));

              Mutant mutant = unit.mock(Mutant.class);
              when(mutant.toOptional()).thenReturn(Optional.empty());

              Request req = unit.get(Request.class);
              when(req.cookie("sid")).thenReturn(mutant);
            })
            .run(unit -> {
              Session session = new ServerSessionManager(unit.get(Config.class),
                  unit.get(Session.Definition.class),
                  unit.get(Session.Store.class), unit.get(ParserExecutor.class))
                      .get(unit.get(Request.class), unit.get(Response.class));
              assertEquals(null, session);
            });
  }

  @Test
  public void getSession() throws Exception {
    String id = "xyz";
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class, Request.class, Response.class, SessionImpl.class)
            .expect(noSecret)
            .expect(cookie)
            .expect(saveInterval(-1L))
            .expect(maxAge(-1))
            .expect(unit -> {
              Cookie.Definition cookie = unit.get(Cookie.Definition.class);
              when(cookie.name()).thenReturn(Optional.of("sid"));

              Mutant mutant = unit.mock(Mutant.class);
              when(mutant.toOptional()).thenReturn(Optional.of(id));

              Request req = unit.get(Request.class);
              when(req.cookie("sid")).thenReturn(mutant);
            })
            .expect(sessionBuilder(id, false, -1))
            .expect(storeGet)
            .run(unit -> {
              Session session = new ServerSessionManager(unit.get(Config.class),
                  unit.get(Session.Definition.class),
                  unit.get(Session.Store.class), unit.get(ParserExecutor.class))
                      .get(unit.get(Request.class), unit.get(Response.class));
              assertEquals(unit.get(SessionImpl.class), session);
            });
  }

  @Test
  public void getTouchSessionCookie() throws Exception {
    String id = "xyz";
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class, Request.class, Response.class, SessionImpl.class)
            .expect(noSecret)
            .expect(cookie)
            .expect(saveInterval(-1L))
            .expect(maxAge(30))
            .expect(unit -> {
              Cookie.Definition cookie = unit.get(Cookie.Definition.class);
              when(cookie.name()).thenReturn(Optional.of("sid"));

              Mutant mutant = unit.mock(Mutant.class);
              when(mutant.toOptional()).thenReturn(Optional.of(id));

              Request req = unit.get(Request.class);
              when(req.cookie("sid")).thenReturn(mutant);
            })
            .expect(sessionBuilder(id, false, TimeUnit.SECONDS.toMillis(30)))
            .expect(storeGet)
            .expect(unsignedCookie(id))
            .expect(session(id))
            .expect(sendCookie())
            .run(unit -> {
              Session session = new ServerSessionManager(unit.get(Config.class),
                  unit.get(Session.Definition.class),
                  unit.get(Session.Store.class), unit.get(ParserExecutor.class))
                      .get(unit.get(Request.class), unit.get(Response.class));
              assertEquals(unit.get(SessionImpl.class), session);
            });
  }

  @Test
  public void getSignedSession() throws Exception {
    String id = "xyz";
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class, Request.class, Response.class, SessionImpl.class)
            .expect(secret("querty"))
            .expect(cookie)
            .expect(saveInterval(-1L))
            .expect(maxAge(-1))
            .expect(unit -> {
              Cookie.Definition cookie = unit.get(Cookie.Definition.class);
              when(cookie.name()).thenReturn(Optional.of("sid"));

              Mutant mutant = unit.mock(Mutant.class);
              when(mutant.toOptional()).thenReturn(Optional.of(id));

              Request req = unit.get(Request.class);
              when(req.cookie("sid")).thenReturn(mutant);
            })
            .expect(unit -> {
              unit.mockStatic(Cookie.Signature.class).when(() -> Cookie.Signature.unsign(id, "querty")).thenReturn("unsigned");
            })
            .expect(sessionBuilder("unsigned", false, -1))
            .expect(storeGet)
            .run(unit -> {
              Session session = new ServerSessionManager(unit.get(Config.class),
                  unit.get(Session.Definition.class),
                  unit.get(Session.Store.class), unit.get(ParserExecutor.class))
                      .get(unit.get(Request.class), unit.get(Response.class));
              assertEquals(unit.get(SessionImpl.class), session);
            });
  }

  @Test
  public void createSession() throws Exception {
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class, Request.class, Response.class, SessionImpl.class)
            .expect(noSecret)
            .expect(cookie)
            .expect(saveInterval(-1L))
            .expect(maxAge(-1))
            .expect(genID("123"))
            .expect(sessionBuilder("123", true, -1))
            .expect(session("123"))
            .expect(unsignedCookie("123"))
            .expect(sendCookie())
            .run(unit -> {
              new ServerSessionManager(unit.get(Config.class), unit.get(Session.Definition.class),
                  unit.get(Session.Store.class), unit.get(ParserExecutor.class))
                      .create(unit.get(Request.class), unit.get(Response.class));
            });
  }

  @Test
  public void createSignedCookieSession() throws Exception {
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class, Request.class, Response.class, SessionImpl.class)
            .expect(secret("querty"))
            .expect(cookie)
            .expect(saveInterval(-1L))
            .expect(maxAge(-1))
            .expect(genID("123"))
            .expect(sessionBuilder("123", true, -1))
            .expect(session("123"))
            .expect(signCookie("querty", "123", "signed"))
            .expect(sendCookie())
            .run(unit -> {
              new ServerSessionManager(unit.get(Config.class), unit.get(Session.Definition.class),
                  unit.get(Session.Store.class), unit.get(ParserExecutor.class))
                      .create(unit.get(Request.class), unit.get(Response.class));
            });
  }

  private Block signCookie(final String secret, final String value, final String signed) {
    return unit -> {
      unit.mockStatic(Cookie.Signature.class).when(() -> Cookie.Signature.sign(value, secret)).thenReturn(signed);

      Cookie.Definition cookie = unit.get(Cookie.Definition.class);
      Cookie.Definition newCookie = unit.constructor(Cookie.Definition.class)
          .build(cookie);

      when(newCookie.value(signed)).thenReturn(newCookie);
      unit.registerMock(Cookie.Definition.class, newCookie);
    };
  }

  private Block secret(final String secret) {
    return unit -> {
      Config config = unit.get(Config.class);
      when(config.hasPath("application.secret")).thenReturn(true);
      when(config.getString("application.secret")).thenReturn(secret);
    };
  }

  private Block unsignedCookie(final String id) {
    return unit -> {
      Cookie.Definition cookie = unit.get(Cookie.Definition.class);
      Cookie.Definition newCookie = unit.constructor(Cookie.Definition.class)
          .build(cookie);

      when(newCookie.value(id)).thenReturn(newCookie);
      unit.registerMock(Cookie.Definition.class, newCookie);
    };
  }

  private Block sendCookie() {
    return unit -> {
      Cookie.Definition cookie = unit.get(Cookie.Definition.class);
      Response rsp = unit.get(Response.class);
      when(rsp.cookie(cookie)).thenReturn(rsp);
    };
  }

  private Block session(final String sid) {
    return unit -> {
      SessionImpl session = unit.get(SessionImpl.class);
      when(session.id()).thenReturn(sid);
    };
  }

  private Block sessionBuilder(final String id, final boolean isNew, final long timeout) {
    return unit -> {
      SessionImpl.Builder builder = unit.constructor(SessionImpl.Builder.class)
          .build(unit.get(ParserExecutor.class), isNew, id, timeout);
      if (isNew) {
        when(builder.build()).thenReturn(unit.get(SessionImpl.class));
      }

      unit.registerMock(Session.Builder.class, builder);
    };
  }

  private Block genID(final String id) {
    return unit -> {
      Store store = unit.get(Session.Store.class);
      when(store.generateID()).thenReturn(id);
    };
  }

  private Block saveInterval(final Long saveInterval) {
    return unit -> {
      Definition session = unit.get(Session.Definition.class);
      when(session.saveInterval()).thenReturn(Optional.of(saveInterval));
    };
  }

  private Block maxAge(final Integer maxAge) {
    return unit -> {
      Cookie.Definition session = unit.get(Cookie.Definition.class);
      when(session.maxAge()).thenReturn(Optional.of(maxAge));
    };
  }
}
