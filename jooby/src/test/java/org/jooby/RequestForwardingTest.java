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

import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.jooby.Request.Forwarding;
import org.jooby.test.MockUnit;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

public class RequestForwardingTest {

  @Test
  public void unwrap() throws Exception {
    new MockUnit(Request.class)
        .run(unit -> {
          Request req = unit.get(Request.class);

          assertEquals(req, Request.Forwarding.unwrap(new Request.Forwarding(req)));

          // 2 level
          assertEquals(req,
              Request.Forwarding.unwrap(new Request.Forwarding(new Request.Forwarding(req))));

          // 3 level
          assertEquals(req, Request.Forwarding.unwrap(new Request.Forwarding(new Request.Forwarding(
              new Request.Forwarding(req)))));

        });
  }

  @Test
  public void path() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.path()).thenReturn("/path");
        })
        .run(unit -> {
          assertEquals("/path", new Request.Forwarding(unit.get(Request.class)).path());
        });

    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.path(true)).thenReturn("/path");
        })
        .run(unit -> {
          assertEquals("/path", new Request.Forwarding(unit.get(Request.class)).path(true));
        });
  }

  @Test
  public void rawPath() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.rawPath()).thenReturn("/path");
        })
        .run(unit -> {
          assertEquals("/path", new Request.Forwarding(unit.get(Request.class)).rawPath());
        });
  }

  @Test
  public void port() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.port()).thenReturn(80);
        })
        .run(unit -> {
          assertEquals(80, new Request.Forwarding(unit.get(Request.class)).port());
        });
  }

  @Test
  public void matches() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.matches("/x")).thenReturn(true);
        })
        .run(unit -> {
          assertEquals(true, new Request.Forwarding(unit.get(Request.class)).matches("/x"));
        });
  }

  @Test
  public void cpath() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.contextPath()).thenReturn("");
        })
        .run(unit -> {
          assertEquals("", new Request.Forwarding(unit.get(Request.class)).contextPath());
        });
  }

  @Test
  public void verb() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.method()).thenReturn("HEAD");
        })
        .run(unit -> {
          assertEquals("HEAD", new Request.Forwarding(unit.get(Request.class)).method());
        });
  }

  @Test
  public void queryString() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.queryString()).thenReturn(Optional.empty());
        })
        .run(unit -> {
          assertEquals(Optional.empty(),
              new Request.Forwarding(unit.get(Request.class)).queryString());
        });
  }

  @Test
  public void type() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.type()).thenReturn(MediaType.json);
        })
        .run(unit -> {
          assertEquals(MediaType.json, new Request.Forwarding(unit.get(Request.class)).type());
        });
  }

  @Test
  public void accept() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.accept()).thenReturn(MediaType.ALL);

          when(req.accepts(MediaType.ALL)).thenReturn(Optional.empty());

          when(req.accepts(MediaType.json, MediaType.js)).thenReturn(Optional.empty());

          when(req.accepts("json", "js")).thenReturn(Optional.empty());
        })
        .run(
            unit -> {
              assertEquals(MediaType.ALL, new Request.Forwarding(unit.get(Request.class)).accept());

              assertEquals(Optional.empty(),
                  new Request.Forwarding(unit.get(Request.class)).accepts(MediaType.ALL));

              assertEquals(Optional.empty(),
                  new Request.Forwarding(unit.get(Request.class)).accepts(MediaType.json,
                      MediaType.js));

              assertEquals(Optional.empty(),
                  new Request.Forwarding(unit.get(Request.class)).accepts("json", "js"));
            });
  }

  @Test
  public void is() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);

          when(req.is(MediaType.ALL)).thenReturn(true);

          when(req.is(MediaType.json, MediaType.js)).thenReturn(true);

          when(req.is("json", "js")).thenReturn(true);
        })
        .run(unit -> {
          assertEquals(true,
              new Request.Forwarding(unit.get(Request.class)).is(MediaType.ALL));

          assertEquals(true,
              new Request.Forwarding(unit.get(Request.class)).is(MediaType.json, MediaType.js));

          assertEquals(true,
              new Request.Forwarding(unit.get(Request.class)).is("json", "js"));
        });
  }

  @Test
  public void isSet() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);

          when(req.isSet("x")).thenReturn(true);
        })
        .run(unit -> {
          assertEquals(true,
              new Request.Forwarding(unit.get(Request.class)).isSet("x"));
        });
  }

  @Test
  public void params() throws Exception {
    new MockUnit(Request.class, Mutant.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.params()).thenReturn(unit.get(Mutant.class));
        })
        .run(unit -> {
          assertEquals(unit.get(Mutant.class),
              new Request.Forwarding(unit.get(Request.class)).params());
        });

    new MockUnit(Request.class, Mutant.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.params("xss")).thenReturn(unit.get(Mutant.class));
        })
        .run(unit -> {
          assertEquals(unit.get(Mutant.class),
              new Request.Forwarding(unit.get(Request.class)).params("xss"));
        });
  }

  @Test
  public void beanParam() throws Exception {
    Object bean = new Object();
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          Mutant params = unit.mock(Mutant.class);
          when(params.to(Object.class)).thenReturn(bean);
          when(params.to(TypeLiteral.get(Object.class))).thenReturn(bean);

          when(req.params()).thenReturn(params);
        })
        .run(
            unit -> {
              assertEquals(bean,
                  new Request.Forwarding(unit.get(Request.class)).params().to(Object.class));

              assertEquals(
                  bean,
                  new Request.Forwarding(unit.get(Request.class)).params().to(
                      TypeLiteral.get(Object.class)));
            });
  }

  @Test
  public void param() throws Exception {
    new MockUnit(Request.class, Mutant.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.param("p")).thenReturn(unit.get(Mutant.class));
        })
        .run(unit -> {
          assertEquals(unit.get(Mutant.class),
              new Request.Forwarding(unit.get(Request.class)).param("p"));
        });

    new MockUnit(Request.class, Mutant.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.param("p", "xss")).thenReturn(unit.get(Mutant.class));
        })
        .run(unit -> {
          assertEquals(unit.get(Mutant.class),
              new Request.Forwarding(unit.get(Request.class)).param("p", "xss"));
        });
  }

  @Test
  public void header() throws Exception {
    new MockUnit(Request.class, Mutant.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.header("h")).thenReturn(unit.get(Mutant.class));
        })
        .run(unit -> {
          assertEquals(unit.get(Mutant.class),
              new Request.Forwarding(unit.get(Request.class)).header("h"));
        });

    new MockUnit(Request.class, Mutant.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.header("h", "xss")).thenReturn(unit.get(Mutant.class));
        })
        .run(unit -> {
          assertEquals(unit.get(Mutant.class),
              new Request.Forwarding(unit.get(Request.class)).header("h", "xss"));
        });
  }

  @Test
  public void headers() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.headers()).thenReturn(Collections.emptyMap());
        })
        .run(unit -> {
          assertEquals(Collections.emptyMap(),
              new Request.Forwarding(unit.get(Request.class)).headers());
        });
  }

  @Test
  public void cookie() throws Exception {
    new MockUnit(Request.class, Mutant.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.cookie("c")).thenReturn(unit.get(Mutant.class));
        })
        .run(unit -> {
          assertEquals(unit.get(Mutant.class),
              new Request.Forwarding(unit.get(Request.class)).cookie("c"));
        });
  }

  @Test
  public void cookies() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.cookies()).thenReturn(Collections.emptyList());
        })
        .run(unit -> {
          assertEquals(Collections.emptyList(),
              new Request.Forwarding(unit.get(Request.class)).cookies());
        });
  }

  @Test
  public void body() throws Exception {
    TypeLiteral<Object> typeLiteral = TypeLiteral.get(Object.class);
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          Mutant body = unit.mock(Mutant.class);
          when(body.to(typeLiteral)).thenReturn(null);
          when(body.to(Object.class)).thenReturn(null);

          when(req.body()).thenReturn(body);
        })
        .run(
            unit -> {
              assertEquals(null,
                  new Request.Forwarding(unit.get(Request.class)).body().to(typeLiteral));

              assertEquals(null,
                  new Request.Forwarding(unit.get(Request.class)).body().to(Object.class));
            });
  }

  @Test
  public void getInstance() throws Exception {
    Key<Object> key = Key.get(Object.class);
    TypeLiteral<Object> typeLiteral = TypeLiteral.get(Object.class);

    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.require(key)).thenReturn(null);

          when(req.require(typeLiteral)).thenReturn(null);

          when(req.require(Object.class)).thenReturn(null);
        })
        .run(
            unit -> {
              assertEquals(null, new Request.Forwarding(unit.get(Request.class)).require(key));

              assertEquals(null,
                  new Request.Forwarding(unit.get(Request.class)).require(typeLiteral));

              assertEquals(null,
                  new Request.Forwarding(unit.get(Request.class)).require(Object.class));
            });
  }

  @Test
  public void charset() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.charset()).thenReturn(Charsets.UTF_8);
        })
        .run(unit -> {
          assertEquals(Charsets.UTF_8, new Request.Forwarding(unit.get(Request.class)).charset());
        });
  }

  @Test
  public void file() throws Exception {
    new MockUnit(Request.class, Upload.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.file("f")).thenReturn(unit.get(Upload.class));
        })
        .run(unit -> {
          assertEquals(unit.get(Upload.class),
              new Request.Forwarding(unit.get(Request.class)).file("f"));
        });
  }

  @SuppressWarnings("unchecked")
  @Test
  public void files() throws Exception {
    new MockUnit(Request.class, List.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.files("f")).thenReturn(unit.get(List.class));
        })
        .run(unit -> {
          assertEquals(unit.get(List.class),
              new Request.Forwarding(unit.get(Request.class)).files("f"));
        });
  }

  @Test
  public void length() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.length()).thenReturn(10L);
        })
        .run(unit -> {
          assertEquals(10L, new Request.Forwarding(unit.get(Request.class)).length());
        });
  }

  @Test
  public void locale() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.locale()).thenReturn(Locale.getDefault());
        })
        .run(
            unit -> {
              assertEquals(Locale.getDefault(),
                  new Request.Forwarding(unit.get(Request.class)).locale());
            });
  }

  @Test
  public void localeLookup() throws Exception {
    BiFunction<List<Locale.LanguageRange>, List<Locale>, Locale> lookup = Locale::lookup;
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.locale(lookup)).thenReturn(Locale.getDefault());
        })
        .run(
            unit -> {
              assertEquals(Locale.getDefault(),
                  new Request.Forwarding(unit.get(Request.class)).locale(lookup));
            });
  }

  @Test
  public void locales() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.locales()).thenReturn(Arrays.asList(Locale.getDefault()));
        })
        .run(
            unit -> {
              assertEquals(Arrays.asList(Locale.getDefault()),
                  new Request.Forwarding(unit.get(Request.class)).locales());
            });
  }

  @Test
  public void localesFilter() throws Exception {
    BiFunction<List<Locale.LanguageRange>, List<Locale>, List<Locale>> lookup = Locale::filter;
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.locales(lookup)).thenReturn(Arrays.asList(Locale.getDefault()));
        })
        .run(unit -> {
          assertEquals(Arrays.asList(Locale.getDefault()),
              new Request.Forwarding(unit.get(Request.class)).locales(lookup));
        });
  }

  @Test
  public void ip() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.ip()).thenReturn("127.0.0.1");
        })
        .run(unit -> {
          assertEquals("127.0.0.1", new Request.Forwarding(unit.get(Request.class)).ip());
        });
  }

  @Test
  public void route() throws Exception {
    new MockUnit(Request.class, Route.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.route()).thenReturn(unit.get(Route.class));
        })
        .run(
            unit -> {
              assertEquals(unit.get(Route.class),
                  new Request.Forwarding(unit.get(Request.class)).route());
            });
  }

  @Test
  public void session() throws Exception {
    new MockUnit(Request.class, Session.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.session()).thenReturn(unit.get(Session.class));
        })
        .run(
            unit -> {
              assertEquals(unit.get(Session.class),
                  new Request.Forwarding(unit.get(Request.class)).session());
            });
  }

  @Test
  public void ifSession() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.ifSession()).thenReturn(Optional.empty());
        })
        .run(
            unit -> {
              assertEquals(Optional.empty(),
                  new Request.Forwarding(unit.get(Request.class)).ifSession());
            });
  }

  @Test
  public void hostname() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.hostname()).thenReturn("localhost");
        })
        .run(unit -> {
          assertEquals("localhost", new Request.Forwarding(unit.get(Request.class)).hostname());
        });
  }

  @Test
  public void protocol() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.protocol()).thenReturn("https");
        })
        .run(unit -> {
          assertEquals("https", new Request.Forwarding(unit.get(Request.class)).protocol());
        });
  }

  @Test
  public void secure() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.secure()).thenReturn(true);
        })
        .run(unit -> {
          assertEquals(true, new Request.Forwarding(unit.get(Request.class)).secure());
        });
  }

  @Test
  public void xhr() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.xhr()).thenReturn(true);
        })
        .run(unit -> {
          assertEquals(true, new Request.Forwarding(unit.get(Request.class)).xhr());
        });
  }

  @SuppressWarnings("unchecked")
  @Test
  public void attributes() throws Exception {
    new MockUnit(Request.class, Map.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.attributes()).thenReturn(unit.get(Map.class));
        })
        .run(unit -> {
          assertEquals(unit.get(Map.class),
              new Request.Forwarding(unit.get(Request.class)).attributes());
        });
  }

  @Test
  public void ifGet() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.ifGet("name")).thenReturn(Optional.of("value"));
        })
        .run(unit -> {
          assertEquals(Optional.of("value"),
              new Request.Forwarding(unit.get(Request.class)).ifGet("name"));
        });
  }

  @Test
  public void get() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.get("name")).thenReturn("value");
        })
        .run(unit -> {
          assertEquals("value",
              new Request.Forwarding(unit.get(Request.class)).get("name"));
        });
  }

  @Test
  public void push() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.push("/path")).thenReturn(req);
        })
        .run(unit -> {
          Forwarding req = new Request.Forwarding(unit.get(Request.class));
          assertEquals(req, req.push("/path"));
        });

    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.push("/path", ImmutableMap.of("k", "v"))).thenReturn(req);
        })
        .run(unit -> {
          Forwarding req = new Request.Forwarding(unit.get(Request.class));
          assertEquals(req, req.push("/path", ImmutableMap.of("k", "v")));
        });
  }

  @Test
  public void getdef() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.get("name", "v")).thenReturn("value");
        })
        .run(unit -> {
          assertEquals("value",
              new Request.Forwarding(unit.get(Request.class)).get("name", "v"));
        });
  }

  @Test
  public void set() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.set("name", "value")).thenReturn(req);
        })
        .run(unit -> {
          assertNotEquals(unit.get(Request.class),
              new Request.Forwarding(unit.get(Request.class)).set("name", "value"));
        });
  }

  @Test
  public void setWithKey() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.set(Key.get(String.class), "value")).thenReturn(req);
        })
        .run(unit -> {
          assertNotEquals(unit.get(Request.class),
              new Request.Forwarding(unit.get(Request.class)).set(Key.get(String.class), "value"));
        });
  }

  @Test
  public void setWithClass() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.set(String.class, "value")).thenReturn(req);
        })
        .run(unit -> {
          assertNotEquals(unit.get(Request.class),
              new Request.Forwarding(unit.get(Request.class)).set(String.class, "value"));
        });
  }

  @Test
  public void setWithTypeLiteral() throws Exception {
    new MockUnit(Request.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.set(TypeLiteral.get(String.class), "value")).thenReturn(req);
        })
        .run(
            unit -> {
              assertNotEquals(unit.get(Request.class),
                  new Request.Forwarding(unit.get(Request.class)).set(
                      TypeLiteral.get(String.class), "value"));
            });
  }

  @Test
  public void unset() throws Exception {
    new MockUnit(Request.class, Map.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.unset("name")).thenReturn(Optional.empty());
        })
        .run(unit -> {
          assertEquals(Optional.empty(),
              new Request.Forwarding(unit.get(Request.class)).unset("name"));
        });
  }

  @Test
  public void timestamp() throws Exception {
    new MockUnit(Request.class, Map.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.timestamp()).thenReturn(1L);
        })
        .run(unit -> {
          assertEquals(1L,
              new Request.Forwarding(unit.get(Request.class)).timestamp());
        });
  }

  @Test
  public void flash() throws Exception {
    new MockUnit(Request.class, Map.class, Request.Flash.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.flash()).thenReturn(unit.get(Request.Flash.class));
        })
        .run(unit -> {
          new Request.Forwarding(unit.get(Request.class)).flash();
        });
  }

  @Test
  public void setFlashAttr() throws Exception {
    new MockUnit(Request.class, Map.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.flash("foo", "bar")).thenReturn(req);
        })
        .run(unit -> {
          assertNotEquals(unit.get(Request.class),
              new Request.Forwarding(unit.get(Request.class)).flash("foo", "bar"));
        });
  }

  @Test
  public void getFlashAttr() throws Exception {
    new MockUnit(Request.class, Map.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.flash("foo")).thenReturn("bar");
        })
        .run(unit -> {
          assertEquals("bar",
              new Request.Forwarding(unit.get(Request.class)).flash("foo"));
        });
  }

  @Test
  public void getIfFlashAttr() throws Exception {
    new MockUnit(Request.class, Map.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          when(req.ifFlash("foo")).thenReturn(Optional.of("bar"));
        })
        .run(unit -> {
          assertEquals("bar",
              new Request.Forwarding(unit.get(Request.class)).ifFlash("foo").get());
        });
  }

  @Test
  public void toStringFwd() throws Exception {
    new MockUnit(Request.class, Map.class)
        .run(unit -> {
          assertEquals(unit.get(Request.class).toString(),
              new Request.Forwarding(unit.get(Request.class)).toString());
        });
  }

  @Test
  public void form() throws Exception {
    RequestForwardingTest v = new RequestForwardingTest();
    new MockUnit(Request.class, Map.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);
          Mutant params = unit.mock(Mutant.class);
          when(params.to(RequestForwardingTest.class)).thenReturn(v);

          when(req.params()).thenReturn(params);
        })
        .run(
            unit -> {
              assertEquals(
                  v,
                  new Request.Forwarding(unit.get(Request.class)).params().to(
                      RequestForwardingTest.class));
            });
  }

  @Test
  public void bodyWithType() throws Exception {
    RequestForwardingTest v = new RequestForwardingTest();
    new MockUnit(Request.class, Map.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);

          when(req.body(RequestForwardingTest.class)).thenReturn(v);
        })
        .run(unit -> {
          assertEquals(
              v,
              new Request.Forwarding(unit.get(Request.class)).body(
                  RequestForwardingTest.class));
        });
  }

  @Test
  public void paramsWithType() throws Exception {
    RequestForwardingTest v = new RequestForwardingTest();
    new MockUnit(Request.class, Map.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);

          when(req.params(RequestForwardingTest.class)).thenReturn(v);
        })
        .run(unit -> {
          assertEquals(
              v,
              new Request.Forwarding(unit.get(Request.class)).params(
                  RequestForwardingTest.class));
        });

    new MockUnit(Request.class, Map.class)
        .expect(unit -> {
          Request req = unit.get(Request.class);

          when(req.params(RequestForwardingTest.class, "xss")).thenReturn(v);
        })
        .run(unit -> {
          assertEquals(
              v,
              new Request.Forwarding(unit.get(Request.class)).params(
                  RequestForwardingTest.class, "xss"));
        });
  }
}
