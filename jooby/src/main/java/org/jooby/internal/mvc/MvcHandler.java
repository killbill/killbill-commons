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

import static java.util.Objects.requireNonNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.jooby.Request;
import org.jooby.Response;
import org.jooby.Route;
import org.jooby.Status;

import org.jooby.funzy.Try;

public class MvcHandler implements Route.MethodHandler {

  private Method handler;

  private Class<?> implementingClass;

  private RequestParamProvider provider;

  /**
   * Constructor for MvcHandler.
   *
   * @param handler the method to handle the request
   * @param implementingClass Target class (method owner).
   * @param provider the request parameter provider
   */
  public MvcHandler(final Method handler, final Class<?> implementingClass,
      final RequestParamProvider provider) {
    this.handler = requireNonNull(handler, "Handler method is required.");
    this.implementingClass = requireNonNull(implementingClass, "Implementing class is required.");
    this.provider = requireNonNull(provider, "Param prodiver is required.");
  }

  @Override
  public Method method() {
    return handler;
  }

  public Class<?> implementingClass() {
    return implementingClass;
  }

  @Override public void handle(Request req, Response rsp, Route.Chain chain) throws Throwable {
    Object result = invoke(req, rsp, chain);
    if (!rsp.committed()) {
      Class<?> returnType = handler.getReturnType();
      if (returnType == void.class) {
        rsp.status(Status.NO_CONTENT);
      } else {
        rsp.status(Status.OK);
        rsp.send(result);
      }
    }
    chain.next(req, rsp);
  }

  @Override public void handle(Request req, Response rsp) throws Throwable {
    // NOOP
  }

  public Object invoke(final Request req, final Response rsp, Route.Chain chain) {
    return Try.apply(() -> {
      Object target = req.require(implementingClass);

      List<RequestParam> parameters = provider.parameters(handler);
      Object[] args = new Object[parameters.size()];
      for (int i = 0; i < parameters.size(); i++) {
        args[i] = parameters.get(i).value(req, rsp, chain);
      }

      final Object result = handler.invoke(target, args);

      return result;
    }).unwrap(InvocationTargetException.class)
        .get();
  }
}
