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
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.jooby.Cookie;
import org.jooby.Mutant;
import org.jooby.Request;
import org.jooby.Response;
import org.jooby.Result;
import org.jooby.Route;
import org.jooby.Route.After;
import org.jooby.Session;
import org.jooby.internal.parser.ParserExecutor;
import org.jooby.test.MockUnit;
import org.jooby.test.MockUnit.Block;
import org.junit.Test;

import static org.mockito.Mockito.doAnswer;

import com.typesafe.config.Config;

public class CookieSessionManagerTest {

  private Block cookie = unit -> {
    Session.Definition sdef = unit.get(Session.Definition.class);
    when(sdef.cookie()).thenReturn(unit.get(Cookie.Definition.class));
  };

  private Block push = unit -> {
    Response rsp = unit.get(Response.class);
    AtomicReference<Route.After> ref = new AtomicReference<>();
    doAnswer(invocation -> {
      ref.set(invocation.getArgument(0));
      return null;
    }).when(rsp).after(org.mockito.ArgumentMatchers.any(Route.After.class));
    unit.registerMock(AtomicReference.class, ref);
  };

  @Test
  public void newInstance() throws Exception {
    String secret = "shhh";
    new MockUnit(Session.Definition.class, ParserExecutor.class, Cookie.Definition.class)
        .expect(cookie)
        .expect(maxAge(-1))
        .run(unit -> {
          new CookieSessionManager(unit.get(ParserExecutor.class),
              unit.get(Session.Definition.class), secret);
        });
  }

  @Test
  public void destroy() throws Exception {
    String secret = "shhh";
    new MockUnit(Session.Definition.class, ParserExecutor.class, Cookie.Definition.class,
        Session.class)
            .expect(cookie)
            .expect(maxAge(-1))
            .run(unit -> {
              new CookieSessionManager(unit.get(ParserExecutor.class),
                  unit.get(Session.Definition.class), secret)
                      .destroy(unit.get(Session.class));
            });
  }

  @Test
  public void requestDone() throws Exception {
    String secret = "shhh";
    new MockUnit(Session.Definition.class, ParserExecutor.class, Cookie.Definition.class,
        Session.class)
            .expect(cookie)
            .expect(maxAge(-1))
            .run(unit -> {
              new CookieSessionManager(unit.get(ParserExecutor.class),
                  unit.get(Session.Definition.class), secret)
                      .requestDone(unit.get(Session.class));
            });
  }

  @Test
  public void create() throws Exception {
    String secret = "shhh";
    new MockUnit(Session.Definition.class, ParserExecutor.class, Cookie.Definition.class,
        Request.class, Response.class, SessionImpl.class)
            .expect(cookie)
            .expect(maxAge(-1))
            .expect(sessionBuilder(Session.COOKIE_SESSION, true, -1))
            .expect(push)
            .run(unit -> {
              Session session = new CookieSessionManager(unit.get(ParserExecutor.class),
                  unit.get(Session.Definition.class), secret)
                      .create(unit.get(Request.class), unit.get(Response.class));
              assertEquals(unit.get(SessionImpl.class), session);
            });
  }

  @SuppressWarnings("unchecked")
  @Test
  public void saveAfter() throws Exception {
    String secret = "shhh";
    String signed = "$#!";
    new MockUnit(Session.Definition.class, ParserExecutor.class, Cookie.Definition.class,
        Request.class, Response.class, SessionImpl.class)
            .expect(cookie)
            .expect(maxAge(-1))
            .expect(sessionBuilder(Session.COOKIE_SESSION, true, -1))
            .expect(push)
            .expect(unit -> {
              Cookie.Definition cookie = unit.get(Cookie.Definition.class);
              when(cookie.name()).thenReturn(Optional.of("sid"));

              Mutant mutant = unit.mock(Mutant.class);
              when(mutant.toOptional()).thenReturn(Optional.of(signed));

              Request req = unit.get(Request.class);
              when(req.cookie("sid")).thenReturn(mutant);
            })
            .expect(unit -> {
              SessionImpl session = unit.get(SessionImpl.class);

              when(session.attributes()).thenReturn(Map.of("foo", "2"));

              Request req = unit.get(Request.class);
              when(req.ifSession()).thenReturn(Optional.of(session));
            })
            .expect(unit -> {
              unit.mockStatic(Cookie.Signature.class).when(() -> Cookie.Signature.unsign(signed, secret)).thenReturn("foo=1");
            })
            .expect(signCookie(secret, "foo=2", "sss"))
            .expect(sendCookie())
            .run(unit -> {
              Session session = new CookieSessionManager(unit.get(ParserExecutor.class),
                  unit.get(Session.Definition.class), secret)
                      .create(unit.get(Request.class), unit.get(Response.class));
              assertEquals(unit.get(SessionImpl.class), session);
            }, unit -> {
              AtomicReference<Route.After> ref = unit.get(AtomicReference.class);
              After next = ref.get();
              Result ok = next.handle(unit.get(Request.class), unit.get(Response.class),
                  org.jooby.Results.ok());
              assertNotNull(ok);
            });
  }

  @SuppressWarnings("unchecked")
  @Test
  public void ignoreSaveAfterIfNoSession() throws Exception {
    String secret = "shhh";
    new MockUnit(Session.Definition.class, ParserExecutor.class, Cookie.Definition.class,
        Request.class, Response.class, SessionImpl.class)
            .expect(cookie)
            .expect(maxAge(-1))
            .expect(sessionBuilder(Session.COOKIE_SESSION, true, -1))
            .expect(push)
            .expect(unit -> {
              Request req = unit.get(Request.class);
              when(req.ifSession()).thenReturn(Optional.empty());
            })
            .run(unit -> {
              Session session = new CookieSessionManager(unit.get(ParserExecutor.class),
                  unit.get(Session.Definition.class), secret)
                      .create(unit.get(Request.class), unit.get(Response.class));
              assertEquals(unit.get(SessionImpl.class), session);
            }, unit -> {
              AtomicReference<Route.After> ref = unit.get(AtomicReference.class);
              After next = ref.get();
              Result ok = next.handle(unit.get(Request.class), unit.get(Response.class),
                  org.jooby.Results.ok());
              assertNotNull(ok);
            });
  }

  @SuppressWarnings("unchecked")
  @Test
  public void saveAfterTouchSession() throws Exception {
    String secret = "shhh";
    String signed = "$#!";
    new MockUnit(Session.Definition.class, ParserExecutor.class, Cookie.Definition.class,
        Request.class, Response.class, SessionImpl.class)
            .expect(cookie)
            .expect(maxAge(30))
            .expect(sessionBuilder(Session.COOKIE_SESSION, true, -1))
            .expect(push)
            .expect(unit -> {
              Cookie.Definition cookie = unit.get(Cookie.Definition.class);
              when(cookie.name()).thenReturn(Optional.of("sid"));

              Mutant mutant = unit.mock(Mutant.class);
              when(mutant.toOptional()).thenReturn(Optional.of(signed));

              Request req = unit.get(Request.class);
              when(req.cookie("sid")).thenReturn(mutant);
            })
            .expect(unit -> {
              SessionImpl session = unit.get(SessionImpl.class);

              when(session.attributes()).thenReturn(Map.of("foo", "1"));

              Request req = unit.get(Request.class);
              when(req.ifSession()).thenReturn(Optional.of(session));
            })
            .expect(unit -> {
              unit.mockStatic(Cookie.Signature.class).when(() -> Cookie.Signature.unsign(signed, secret)).thenReturn("foo=1");
            })
            .expect(unit -> {
              Cookie.Definition cookie = unit.get(Cookie.Definition.class);
              Cookie.Definition newCookie = unit.constructor(Cookie.Definition.class)
                  .build(cookie);

              when(newCookie.value(signed)).thenReturn(newCookie);
              unit.registerMock(Cookie.Definition.class, newCookie);
            })
            .expect(sendCookie())
            .run(unit -> {
              Session session = new CookieSessionManager(unit.get(ParserExecutor.class),
                  unit.get(Session.Definition.class), secret)
                      .create(unit.get(Request.class), unit.get(Response.class));
              assertEquals(unit.get(SessionImpl.class), session);
            }, unit -> {
              AtomicReference<Route.After> ref = unit.get(AtomicReference.class);
              After next = ref.get();
              Result ok = next.handle(unit.get(Request.class), unit.get(Response.class),
                  org.jooby.Results.ok());
              assertNotNull(ok);
            });
  }

  private Block sendCookie() {
    return unit -> {
      Cookie.Definition cookie = unit.get(Cookie.Definition.class);
      Response rsp = unit.get(Response.class);
      when(rsp.cookie(cookie)).thenReturn(rsp);
    };
  }

  @Test
  public void noSession() throws Exception {
    String secret = "shh";
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class, Request.class, Response.class)
            .expect(cookie)
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
              Session session = new CookieSessionManager(unit.get(ParserExecutor.class),
                  unit.get(Session.Definition.class), secret).get(unit.get(Request.class),
                      unit.get(Response.class));
              assertEquals(null, session);
            });
  }

  @Test
  public void getSession() throws Exception {
    String secret = "shh";
    String signed = "$#!";
    new MockUnit(Config.class, Session.Definition.class, Cookie.Definition.class,
        Session.Store.class, ParserExecutor.class, Request.class, Response.class, SessionImpl.class)
            .expect(cookie)
            .expect(maxAge(-1))
            .expect(unit -> {
              Cookie.Definition cookie = unit.get(Cookie.Definition.class);
              when(cookie.name()).thenReturn(Optional.of("sid"));

              Mutant mutant = unit.mock(Mutant.class);
              when(mutant.toOptional()).thenReturn(Optional.of(signed));

              Request req = unit.get(Request.class);
              when(req.cookie("sid")).thenReturn(mutant);
            })
            .expect(unit -> {
              unit.mockStatic(Cookie.Signature.class).when(() -> Cookie.Signature.unsign(signed, secret)).thenReturn("foo=1");
            })
            .expect(sessionBuilder(Session.COOKIE_SESSION, false, -1))
            .expect(unit -> {
              Session.Builder builder = unit.get(Session.Builder.class);
              when(builder.set(Map.of("foo", "1"))).thenReturn(builder);
              when(builder.build()).thenReturn(unit.get(SessionImpl.class));
            })
            .expect(push)
            .run(unit -> {
              Session session = new CookieSessionManager(unit.get(ParserExecutor.class),
                  unit.get(Session.Definition.class), secret).get(unit.get(Request.class),
                      unit.get(Response.class));
              assertEquals(unit.get(SessionImpl.class), session);
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

  private Block maxAge(final Integer maxAge) {
    return unit -> {
      Cookie.Definition session = unit.get(Cookie.Definition.class);
      when(session.maxAge()).thenReturn(Optional.of(maxAge));
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

}
