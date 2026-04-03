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

import org.jooby.Request;
import org.jooby.Response;
import org.jooby.Route;
import org.jooby.Route.Chain;
import org.jooby.Route.Filter;
import org.jooby.Route.Mapper;
import org.jooby.funzy.Throwing;
import org.jooby.funzy.Try;

@SuppressWarnings({"unchecked", "rawtypes"})
public class MappedHandler implements Filter {

  private Throwing.Function3<Request, Response, Route.Chain, Object> supplier;
  private Mapper mapper;

  public MappedHandler(final Throwing.Function3<Request, Response, Route.Chain, Object> supplier,
      final Route.Mapper mapper) {
    this.supplier = supplier;
    this.mapper = mapper;
  }

  public MappedHandler(final Throwing.Function2<Request, Response, Object> supplier,
      final Route.Mapper mapper) {
    this((req, rsp, chain) -> supplier.apply(req, rsp), mapper);
  }

  @Override
  public void handle(final Request req, final Response rsp, final Chain chain) throws Throwable {
    Object input = supplier.apply(req, rsp, chain);
    Object output = Try
        .apply(() -> mapper.map(input))
        .recover(ClassCastException.class, input)
        .get();
    rsp.send(output);
    chain.next(req, rsp);
  }

}
