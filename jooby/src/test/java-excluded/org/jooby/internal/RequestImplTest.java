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

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import org.jooby.Err;
import org.jooby.Route;
import org.jooby.spi.NativeRequest;
import org.jooby.test.MockUnit;
import org.jooby.test.MockUnit.Block;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;

public class RequestImplTest {

  private Block accept = unit -> {
    NativeRequest req = unit.get(NativeRequest.class);
    expect(req.header("Accept")).andReturn(Optional.of("*/*"));
  };

  private Block contentType = unit -> {
    NativeRequest req = unit.get(NativeRequest.class);
    expect(req.header("Content-Type")).andReturn(Optional.empty());
  };

  private Block acceptLan = unit -> {
    NativeRequest req = unit.get(NativeRequest.class);
    expect(req.header("Accept-Language")).andReturn(Optional.empty());
  };

  @Test
  public void defaults() throws Exception {
    new MockUnit(Injector.class, NativeRequest.class, Route.class)
        .expect(accept)
        .expect(acceptLan)
        .expect(contentType)
        .run(unit -> {
          new RequestImpl(unit.get(Injector.class), unit.get(NativeRequest.class), "/", 8080,
              unit.get(Route.class), StandardCharsets.UTF_8, ImmutableList.of(Locale.ENGLISH),
              ImmutableMap.of(), ImmutableMap.of(), 1L);
        });
  }

  @Test
  public void matches() throws Exception {
    new MockUnit(Injector.class, NativeRequest.class, Route.class)
        .expect(accept)
        .expect(acceptLan)
        .expect(contentType)
        .expect(unit -> {
          Route route = unit.get(Route.class);
          expect(route.path()).andReturn("/path/x");
        })

        .run(unit -> {
          RequestImpl req = new RequestImpl(unit.get(Injector.class), unit.get(NativeRequest.class),
              "/", 8080,
              unit.get(Route.class), StandardCharsets.UTF_8, ImmutableList.of(Locale.ENGLISH),
              ImmutableMap.of(),
              ImmutableMap.of(), 1L);
          assertEquals(true, req.matches("/path/**"));
        });
  }

  @Test
  public void lang() throws Exception {
    new MockUnit(Injector.class, NativeRequest.class, Route.class)
        .expect(accept)
        .expect(unit -> {
          NativeRequest req = unit.get(NativeRequest.class);
          expect(req.header("Accept-Language")).andReturn(Optional.of("en"));
        })
        .expect(contentType)
        .run(unit -> {
          RequestImpl req = new RequestImpl(unit.get(Injector.class), unit.get(NativeRequest.class),
              "/", 8080,
              unit.get(Route.class), StandardCharsets.UTF_8, ImmutableList.of(Locale.ENGLISH),
              ImmutableMap.of(),
              ImmutableMap.of(), 1L);
          assertEquals(Locale.ENGLISH, req.locale());
        });
  }

  @Test
  public void files() throws Exception {
    IOException cause = new IOException("intentional err");
    new MockUnit(Injector.class, NativeRequest.class, Route.class)
        .expect(accept)
        .expect(acceptLan)
        .expect(contentType)
        .expect(unit -> {
          NativeRequest req = unit.get(NativeRequest.class);
          expect(req.files("f")).andThrow(cause);
        })
        .run(unit -> {
          try {
            new RequestImpl(unit.get(Injector.class), unit.get(NativeRequest.class), "/", 8080,
                unit.get(Route.class), StandardCharsets.UTF_8, ImmutableList.of(Locale.ENGLISH),
                ImmutableMap.of(),
                ImmutableMap.of(), 1L).file("f");
            fail("expecting error");
          } catch (IOException ex) {
            assertEquals(cause, ex);
          }
        });
  }

  @Test
  public void paramNames() throws Exception {
    IOException cause = new IOException("intentional err");
    new MockUnit(Injector.class, NativeRequest.class, Route.class)
        .expect(accept)
        .expect(acceptLan)
        .expect(contentType)
        .expect(unit -> {
          Route route = unit.get(Route.class);
          expect(route.vars()).andReturn(ImmutableMap.of());

          NativeRequest req = unit.get(NativeRequest.class);
          expect(req.paramNames()).andThrow(cause);
        })
        .run(unit -> {
          try {
            new RequestImpl(unit.get(Injector.class), unit.get(NativeRequest.class), "/", 8080,
                unit.get(Route.class), StandardCharsets.UTF_8, ImmutableList.of(Locale.ENGLISH),
                ImmutableMap.of(),
                ImmutableMap.of(), 1L).params();
            fail("expecting error");
          } catch (Err ex) {
            assertEquals(400, ex.statusCode());
            assertEquals(cause, ex.getCause());
          }
        });
  }

  @Test
  public void params() throws Exception {
    IOException cause = new IOException("intentional err");
    new MockUnit(Injector.class, NativeRequest.class, Route.class)
        .expect(accept)
        .expect(acceptLan)
        .expect(contentType)
        .expect(unit -> {
          Route route = unit.get(Route.class);
          expect(route.vars()).andReturn(ImmutableMap.of());

          NativeRequest req = unit.get(NativeRequest.class);
          expect(req.params("p")).andThrow(cause);
        })
        .run(unit -> {
          try {
            new RequestImpl(unit.get(Injector.class), unit.get(NativeRequest.class), "/", 8080,
                unit.get(Route.class), StandardCharsets.UTF_8, ImmutableList.of(Locale.ENGLISH),
                ImmutableMap.of(),
                ImmutableMap.of(), 1L).param("p");
            fail("expecting error");
          } catch (Err ex) {
            assertEquals(400, ex.statusCode());
            assertEquals(cause, ex.getCause());
          }
        });
  }

}
