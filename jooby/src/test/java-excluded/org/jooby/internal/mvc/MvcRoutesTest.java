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

import static org.easymock.EasyMock.expect;

import java.util.List;

import org.jooby.Env;
import org.jooby.Route.Definition;
import org.jooby.internal.RouteMetadata;
import org.jooby.mvc.GET;
import org.jooby.test.MockUnit;
import org.junit.Test;

public class MvcRoutesTest {

  public static class NoPath {

    @GET
    public void nopath() {

    }

  }

  @Test
  public void emptyConstructor() throws Exception {
    new MvcRoutes();

  }

  @Test(expected = IllegalArgumentException.class)
  public void nopath() throws Exception {
    new MockUnit(Env.class)
        .expect(unit -> {
          Env env = unit.get(Env.class);
          expect(env.name()).andReturn("dev").times(2);
        })
        .run(unit -> {
          Env env = unit.get(Env.class);
          List<Definition> routes = MvcRoutes.routes(env, new RouteMetadata(env), "",
              true, NoPath.class);
          System.out.println(routes);
        });
  }
}
