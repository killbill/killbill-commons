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
package org.jooby.internal.parser;

import com.google.common.primitives.Primitives;
import com.google.common.reflect.Reflection;
import com.google.inject.TypeLiteral;
import org.jooby.Err;
import org.jooby.Mutant;
import org.jooby.Parser;
import org.jooby.Request;
import org.jooby.Response;
import org.jooby.Route;
import org.jooby.internal.ParameterNameProvider;
import org.jooby.internal.mvc.RequestParam;
import org.jooby.internal.parser.bean.BeanPlan;
import org.jooby.funzy.Try;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class BeanParser implements Parser {

  private Function<? super Throwable, Try.Value<? extends Object>> MISSING = x -> {
    return x instanceof Err.Missing ? Try.success(null) : Try.failure(x);
  };

  private Function<? super Throwable, Try.Value<? extends Object>> RETHROW = Try::failure;

  private Function<? super Throwable, Try.Value<? extends Object>> recoverMissing;

  @SuppressWarnings("rawtypes")
  private final Map<TypeLiteral, BeanPlan> forms;

  public BeanParser(final boolean allowNulls) {
    this.recoverMissing = allowNulls ? MISSING : RETHROW;
    this.forms = new ConcurrentHashMap<>();
  }

  @Override
  public Object parse(final TypeLiteral<?> type, final Context ctx) throws Throwable {
    Class<?> beanType = type.getRawType();
    if (Primitives.isWrapperType(Primitives.wrap(beanType))
        || CharSequence.class.isAssignableFrom(beanType)) {
      return ctx.next();
    }
    return ctx.ifparams(map -> {
      final Object bean;
      if (List.class.isAssignableFrom(beanType)) {
        bean = newBean(ctx.require(Request.class), ctx.require(Response.class),
            ctx.require(Route.Chain.class), map, type);
      } else if (beanType.isInterface()) {
        bean = newBeanInterface(ctx.require(Request.class), ctx.require(Response.class),
            ctx.require(Route.Chain.class), beanType);
      } else {
        bean = newBean(ctx.require(Request.class), ctx.require(Response.class),
            ctx.require(Route.Chain.class), map, type);
      }

      return bean;
    });
  }

  @Override
  public String toString() {
    return "bean";
  }

  @SuppressWarnings("rawtypes")
  private Object newBean(final Request req, final Response rsp, final Route.Chain chain,
      final Map<String, Mutant> params, final TypeLiteral type) throws Throwable {
    BeanPlan form = forms.get(type);
    if (form == null) {
      form = new BeanPlan(req.require(ParameterNameProvider.class), type);
      forms.put(type, form);
    }
    return form.newBean(p -> value(p, req, rsp, chain), params.keySet());
  }

  private Object newBeanInterface(final Request req, final Response rsp, final Route.Chain chain,
      final Class<?> beanType) {
    return Reflection.newProxy(beanType, (proxy, method, args) -> {
      StringBuilder name = new StringBuilder(method.getName()
          .replace("get", "")
          .replace("is", ""));
      name.setCharAt(0, Character.toLowerCase(name.charAt(0)));
      return value(new RequestParam(method, name.toString(), method.getGenericReturnType()), req,
          rsp, chain);
    });
  }

  private Object value(final RequestParam param, final Request req, final Response rsp,
      final Route.Chain chain)
      throws Throwable {
    return Try.apply(() -> param.value(req, rsp, chain))
        .recover(x -> recoverMissing.apply(x).get())
        .get();
  }

}
