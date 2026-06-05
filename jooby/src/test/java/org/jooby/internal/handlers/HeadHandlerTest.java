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
package org.jooby.internal.handlers;

import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.jooby.MediaType;
import org.jooby.Request;
import org.jooby.Response;
import org.jooby.Route;
import org.jooby.Route.Chain;
import org.jooby.Route.Definition;
import org.jooby.internal.RouteImpl;
import org.jooby.test.MockUnit;
import org.jooby.test.MockUnit.Block;
import org.junit.Test;


public class HeadHandlerTest {

  private Block path = unit -> {
    Request req = unit.get(Request.class);
    when(req.path()).thenReturn("/");
  };

  private Block len = unit -> {
    Response rsp = unit.get(Response.class);
    when(rsp.length(0)).thenReturn(rsp);
  };

  private Block next = unit -> {
    Chain chain = unit.get(Route.Chain.class);
    chain.next(unit.get(Request.class), unit.get(Response.class));
  };

  @Test
  public void handle() throws Exception {
    new MockUnit(Request.class, Response.class, Route.Chain.class, Route.Definition.class)
        .expect(path)
        .expect(unit -> {
          Route.Definition routeDef = unit.get(Route.Definition.class);
          when(routeDef.glob()).thenReturn(false);

          RouteImpl route = unit.mock(RouteImpl.class);
          route.handle(unit.get(Request.class), unit.get(Response.class),
              unit.get(Route.Chain.class));

          Optional<Route> ifRoute = Optional.of(route);

          when(routeDef.matches(Route.GET, "/", MediaType.all, MediaType.ALL)).thenReturn(ifRoute);
        })
        .expect(len)
        .run(unit -> {
          Set<Definition> routes = new HashSet<>(Collections.singleton(unit.get(Route.Definition.class)));
          new HeadHandler(routes)
              .handle(unit.get(Request.class), unit.get(Response.class),
                  unit.get(Route.Chain.class));
        });
  }

  @Test
  public void noRoute() throws Exception {
    new MockUnit(Request.class, Response.class, Route.Chain.class, Route.Definition.class)
        .expect(path)
        .expect(unit -> {
          Route.Definition routeDef = unit.get(Route.Definition.class);
          when(routeDef.glob()).thenReturn(false);

          Optional<Route> ifRoute = Optional.empty();

          when(routeDef.matches(Route.GET, "/", MediaType.all, MediaType.ALL)).thenReturn(ifRoute);
        })
        .expect(next)
        .run(unit -> {
          Set<Definition> routes = new HashSet<>(Collections.singleton(unit.get(Route.Definition.class)));
          new HeadHandler(routes)
              .handle(unit.get(Request.class), unit.get(Response.class),
                  unit.get(Route.Chain.class));
        });
  }

  @Test
  public void ignoreGlob() throws Exception {
    new MockUnit(Request.class, Response.class, Route.Chain.class, Route.Definition.class)
        .expect(path)
        .expect(unit -> {
          Route.Definition routeDef = unit.get(Route.Definition.class);
          when(routeDef.glob()).thenReturn(true);
        })
        .expect(next)
        .run(unit -> {
          Set<Definition> routes = new HashSet<>(Collections.singleton(unit.get(Route.Definition.class)));
          new HeadHandler(routes)
              .handle(unit.get(Request.class), unit.get(Response.class),
                  unit.get(Route.Chain.class));
        });
  }

  @Test
  public void noroutes() throws Exception {
    new MockUnit(Request.class, Response.class, Route.Chain.class, Route.Definition.class)
        .expect(path)
        .expect(next)
        .run(unit -> {
          Set<Definition> routes = new HashSet<>();
          new HeadHandler(routes)
              .handle(unit.get(Request.class), unit.get(Response.class),
                  unit.get(Route.Chain.class));
        });
  }

}
