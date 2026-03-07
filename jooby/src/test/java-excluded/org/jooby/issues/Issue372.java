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
package org.jooby.issues;

import static org.easymock.EasyMock.expect;

import org.jooby.Env;
import org.jooby.Result;
import org.jooby.Results;
import org.jooby.internal.RouteMetadata;
import org.jooby.internal.mvc.MvcRoutes;
import org.jooby.mvc.GET;
import org.jooby.mvc.Path;
import org.jooby.test.MockUnit;
import org.junit.Test;

public class Issue372 {

  public static class PingRoute {
    @Path("/ping")
    @GET
    private Result ping() {
      return Results.ok();
    }
  }

  public static class Ext extends PingRoute {
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldFailFastOnPrivateMvcRoutes() throws Exception {
    new MockUnit(Env.class)
        .expect(unit -> {
          Env env = unit.get(Env.class);
          expect(env.name()).andReturn("dev").times(2);
        })
        .run(unit -> {
          Env env = unit.get(Env.class);
          MvcRoutes.routes(env, new RouteMetadata(env), "", true, PingRoute.class);
        });
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldFailFastOnPrivateMvcRoutesExt() throws Exception {
    new MockUnit(Env.class)
        .expect(unit -> {
          Env env = unit.get(Env.class);
          expect(env.name()).andReturn("dev").times(2);
        })
        .run(unit -> {
          Env env = unit.get(Env.class);
          MvcRoutes.routes(env, new RouteMetadata(env), "", true, Ext.class);
        });
  }

}
