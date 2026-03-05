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

import java.util.Collections;
import java.util.Optional;

import org.jooby.Err;
import org.jooby.MediaType;
import org.jooby.Request;
import org.jooby.Response;
import org.jooby.Route;
import org.jooby.Route.Source;
import org.jooby.test.MockUnit;
import org.junit.Test;

public class RouteImplTest {

  @Test(expected = Err.class)
  public void notFound() throws Exception {
    new MockUnit(Request.class, Response.class, Route.Chain.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);
          expect(rsp.status()).andReturn(Optional.empty());

          Request req = unit.get(Request.class);
          expect(req.path()).andReturn("/x");
        })
        .run(unit -> {
          RouteImpl.notFound("GET", "/x")
              .handle(unit.get(Request.class), unit.get(Response.class),
                  unit.get(Route.Chain.class));
        });
  }

  @Test
  public void statusSetOnNotFound() throws Exception {
    new MockUnit(Request.class, Response.class, Route.Chain.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);
          expect(rsp.status()).andReturn(Optional.of(org.jooby.Status.OK));
        })
        .run(unit -> {
          RouteImpl.notFound("GET", "/x")
              .handle(unit.get(Request.class), unit.get(Response.class),
                  unit.get(Route.Chain.class));
        });
  }

  @Test
  public void toStr() {
    Route.Filter f = (req, rsp, chain) -> {
    };
    Route route = new RouteImpl(f, new Route.Definition("GET", "/p?th", f)
        .name("path")
        .consumes("html", "json"), "GET", "/path", MediaType.valueOf("json", "html"),
        Collections.emptyMap(), null, Source.BUILTIN);

    assertEquals(
        "| Method | Path  | Source   | Name  | Pattern | Consumes                      | Produces                      |\n"
            +
            "|--------|-------|----------|-------|---------|-------------------------------|-------------------------------|\n"
            +
            "| GET    | /path | ~builtin | /path | /p?th   | [text/html, application/json] | [application/json, text/html] |",
        route.toString());
  }

  @Test
  public void consumes() {
    Route.Filter f = (req, rsp, chain) -> {
    };
    Route route = new RouteImpl(f, new Route.Definition("GET", "/p?th", f).consumes("html", "json"),
        "GET", "/path", Collections.emptyList(), Collections.emptyMap(), null, Source.BUILTIN);

    assertEquals(MediaType.valueOf("html", "json"), route.consumes());
  }

}
