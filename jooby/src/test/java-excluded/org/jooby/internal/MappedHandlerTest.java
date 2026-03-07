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
import org.jooby.Request;
import org.jooby.Response;
import org.jooby.Route;
import org.jooby.test.MockUnit;
import org.jooby.funzy.Throwing;
import org.junit.Test;

public class MappedHandlerTest {

  @SuppressWarnings("unchecked")
  @Test
  public void shouldIgnoreClassCastExceptionWhileMapping() throws Exception {
    Route.Mapper<Integer> m = value -> value.intValue() * 2;
    String value = "1";
    new MockUnit(Throwing.Function2.class, Request.class, Response.class, Route.Chain.class)
        .expect(unit -> {
          Throwing.Function2<Request, Response, Object> fn = unit.get(Throwing.Function2.class);
          expect(fn.apply(unit.get(Request.class), unit.get(Response.class))).andReturn(value);
        })
        .expect(unit -> {
          Route.Chain chain = unit.get(Route.Chain.class);
          Request req = unit.get(Request.class);
          Response rsp = unit.get(Response.class);
          rsp.send(value);
          chain.next(req, rsp);
        })
        .run(unit -> {
          new MappedHandler(unit.get(Throwing.Function2.class), m)
              .handle(unit.get(Request.class), unit.get(Response.class),
                  unit.get(Route.Chain.class));
        });
  }
}
