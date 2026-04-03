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

import org.jooby.Err;
import org.jooby.MediaType;
import org.jooby.Request;
import org.jooby.Response;
import org.jooby.Route;
import org.jooby.Status;
import org.jooby.internal.mvc.MvcHandler;

import java.util.List;
import java.util.Map;

public class RouteImpl implements RouteWithFilter {

  private Definition route;

  private String path;

  private Map<Object, String> vars;

  private Filter filter;

  private List<MediaType> produces;

  private String method;

  private Source source;

  public static RouteWithFilter notFound(final String method, final String path) {
    return new FallbackRoute("404", method, path, MediaType.ALL, (req, rsp, chain) -> {
      if (!rsp.status().isPresent()) {
        throw new Err(Status.NOT_FOUND, req.path());
      }
    });
  }

  public static RouteWithFilter fallback(final Filter filter, final String method,
    final String path, final String name, final List<MediaType> produces) {
    return new FallbackRoute(name, method, path, produces, filter);
  }

  public RouteImpl(final Filter filter, final Definition route, final String method,
    final String path, final List<MediaType> produces, final Map<Object, String> vars,
    final Mapper<?> mapper, final Source source) {
    this.filter = filter;
    if (mapper != null) {
      if (filter instanceof Route.OneArgHandler) {
        this.filter = new MappedHandler((req, rsp) -> ((Route.OneArgHandler) filter).handle(req),
          mapper);
      } else if (filter instanceof Route.ZeroArgHandler) {
        this.filter = new MappedHandler((req, rsp) -> ((Route.ZeroArgHandler) filter).handle(),
          mapper);
      } else if (filter instanceof MvcHandler) {
        if (((MvcHandler) filter).method().getReturnType() == void.class) {
          this.filter = filter;
        } else {
          this.filter = new MappedHandler((req, rsp, chain) -> ((MvcHandler) filter).invoke(req, rsp,
              chain),
            mapper);
        }
      } else {
        this.filter = filter;
      }
    }
    this.route = route;
    this.method = method;
    this.produces = produces;
    this.vars = vars;
    this.source = source;
    this.path = Route.unerrpath(path);
  }

  @Override
  public void handle(final Request request, final Response response, final Chain chain)
    throws Throwable {
    filter.handle(request, response, chain);
  }

  @Override
  public Map<String, Object> attributes() {
    return route.attributes();
  }

  @Override
  public String path() {
    return path;
  }

  @Override
  public String method() {
    return method;
  }

  @Override
  public String pattern() {
    return route.pattern().substring(route.pattern().indexOf('/'));
  }

  @Override
  public String name() {
    return route.name();
  }

  @Override
  public Map<Object, String> vars() {
    return vars;
  }

  @Override
  public List<MediaType> consumes() {
    return route.consumes();
  }

  @Override
  public List<MediaType> produces() {
    return produces;
  }

  @Override
  public boolean glob() {
    return route.glob();
  }

  @Override
  public String reverse(final Map<String, Object> vars) {
    return route.reverse(vars);
  }

  @Override
  public String reverse(final Object... values) {
    return route.reverse(values);
  }

  @Override
  public Source source() {
    return source;
  }

  @Override
  public String renderer() {
    return route.renderer();
  }

  @Override
  public String toString() {
    return print();
  }

}
