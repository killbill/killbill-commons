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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Types;
import org.jooby.Cookie;
import org.jooby.Err;
import org.jooby.Mutant;
import org.jooby.Request;
import org.jooby.Response;
import org.jooby.Route;
import org.jooby.Session;
import org.jooby.Status;
import org.jooby.Upload;
import org.jooby.mvc.Body;
import org.jooby.mvc.Flash;
import org.jooby.mvc.Header;
import org.jooby.mvc.Local;

import javax.inject.Named;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings({"rawtypes", "unchecked" })
public class RequestParam {

  private interface GetValue {

    Object apply(Request req, Response rsp, Route.Chain chain, RequestParam param) throws Exception;

  }

  private static final TypeLiteral<Header> headerType = TypeLiteral.get(Header.class);

  private static final TypeLiteral<Body> bodyType = TypeLiteral.get(Body.class);

  private static final TypeLiteral<Local> localType = TypeLiteral.get(Local.class);

  private static final TypeLiteral<Flash> flashType = TypeLiteral.get(Flash.class);

  private static final Map<Object, GetValue> injector;

  static {
    Builder<Object, GetValue> builder = ImmutableMap.<Object, GetValue> builder();
    /**
     * Body
     */
    builder.put(bodyType, (req, rsp, chain, param) -> req.body().to(param.type));
    /**
     * Request
     */
    builder.put(TypeLiteral.get(Request.class), (req, rsp, chain, param) -> req);
    /**
     * Route
     */
    builder.put(TypeLiteral.get(Route.class), (req, rsp, chain, param) -> req.route());
    /**
     * Response
     */
    builder.put(TypeLiteral.get(Response.class), (req, rsp, chain, param) -> rsp);
    /**
     * Route.Chain
     */
    builder.put(TypeLiteral.get(Route.Chain.class), (req, rsp, chain, param) -> chain);
    /**
     * Session
     */
    builder.put(TypeLiteral.get(Session.class), (req, rsp, chain, param) -> req.session());
    builder.put(TypeLiteral.get(Types.newParameterizedType(Optional.class, Session.class)),
        (req, rsp, chain, param) -> req.ifSession());

    /**
     * Files
     */
    builder.put(TypeLiteral.get(Upload.class), (req, rsp, chain, param) -> req.file(param.name));
    builder.put(TypeLiteral.get(Types.newParameterizedType(Optional.class, Upload.class)),
        (req, rsp, chain, param) -> {
          List<Upload> files = req.files(param.name);
          return files.size() == 0 ? Optional.empty() : Optional.of(files.get(0));
        });
    builder.put(TypeLiteral.get(Types.newParameterizedType(List.class, Upload.class)),
        (req, rsp, chain, param) -> req.files(param.name));

    /**
     * Cookie
     */
    builder.put(TypeLiteral.get(Cookie.class), (req, rsp, chain, param) -> req.cookies().stream()
        .filter(c -> c.name().equalsIgnoreCase(param.name)).findFirst().get());
    builder.put(TypeLiteral.get(Types.listOf(Cookie.class)), (req, rsp, chain, param) -> req.cookies());
    builder.put(TypeLiteral.get(Types.newParameterizedType(Optional.class, Cookie.class)),
        (req, rsp, chain, param) -> req.cookies().stream()
            .filter(c -> c.name().equalsIgnoreCase(param.name)).findFirst());
    /**
     * Header
     */
    builder.put(headerType, (req, rsp, chain, param) -> req.header(param.name).to(param.type));

    /**
     * Local
     */
    builder.put(localType, (req, rsp, chain, param) -> {
      Optional local = req.ifGet(param.name);
      if (param.optional) {
        return local;
      }
      if(local.isPresent()) {
        return local.get();
      }
      if (param.type.getRawType() == Map.class) {
        return req.attributes();
      }
      throw new Err(Status.SERVER_ERROR, "Could not find required local '" + param.name + "', which was required on " + req.path());
    });

    /**
     * Flash
     */
    builder.put(flashType, (req, rsp, chain, param) -> {
      Class rawType = param.type.getRawType();
      if (Map.class.isAssignableFrom(rawType)) {
        return req.flash();
      }
      return param.optional ? req.ifFlash(param.name) : req.flash(param.name);
    });

    injector = builder.build();
  }

  public final String name;

  public final TypeLiteral type;

  private final GetValue strategy;

  private boolean optional;

  public RequestParam(final Parameter parameter, final String name) {
    this(parameter, name, parameter.getParameterizedType());
  }

  public RequestParam(final AnnotatedElement elem, final String name, final Type type) {
    this.name = name;
    this.type = TypeLiteral.get(type);
    this.optional = this.type.getRawType() == Optional.class;
    final TypeLiteral strategyType;
    if (elem.getAnnotation(Header.class) != null) {
      strategyType = headerType;
    } else if (elem.getAnnotation(Body.class) != null) {
      strategyType = bodyType;
    } else if (elem.getAnnotation(Local.class) != null) {
      strategyType = localType;
    } else if (elem.getAnnotation(Flash.class) != null) {
      strategyType = flashType;
    } else {
      strategyType = this.type;
    }
    this.strategy = injector.getOrDefault(strategyType, param());
  }

  public Object value(final Request req, final Response rsp, final Route.Chain chain) throws Throwable {
    return strategy.apply(req, rsp, chain, this);
  }

  public static String nameFor(final Parameter param) {
    String name = findName(param);
    return name == null ? (param.isNamePresent() ? param.getName() : null) : name;
  }

  private static String findName(final AnnotatedElement elem) {
    Named named = elem.getAnnotation(Named.class);
    if (named == null) {
      com.google.inject.name.Named gnamed = elem
          .getAnnotation(com.google.inject.name.Named.class);
      if (gnamed == null) {
        Header header = elem.getAnnotation(Header.class);
        if (header == null) {
          return null;
        }
        return Strings.emptyToNull(header.value());
      }
      return gnamed.value();
    }
    return Strings.emptyToNull(named.value());
  }

  private static final GetValue param() {
    return (req, rsp, chain, param) -> {
      Mutant mutant = req.param(param.name);
      if (mutant.isSet() || param.optional) {
        return mutant.to(param.type);
      }
      try {
        return req.params().to(param.type);
      } catch (Err ex) {
        // force parsing
        return mutant.to(param.type);
      }
    };
  }
}
