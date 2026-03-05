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

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.jooby.Parser;

import com.google.inject.TypeLiteral;

public class StaticMethodParser implements Parser {

  private final String methodName;

  public StaticMethodParser(final String methodName) {
    this.methodName = requireNonNull(methodName, "A method's name is required.");
  }

  public boolean matches(final TypeLiteral<?> toType) {
    try {
      return method(toType.getRawType()) != null;
    } catch (NoSuchMethodException x) {
      return false;
    }
  }

  @Override
  public Object parse(final TypeLiteral<?> type, final Parser.Context ctx) throws Exception {
    return ctx.param(params -> {
      try {
        return method(type.getRawType()).invoke(null, params.get(0));
      } catch (NoSuchMethodException x) {
        return ctx.next();
      }
    });
  }

  public Object parse(final TypeLiteral<?> type, final Object value) throws Exception {
    return method(type.getRawType()).invoke(null, value);
  }

  private Method method(final Class<?> rawType) throws NoSuchMethodException {
    Method method = rawType.getDeclaredMethod(methodName, String.class);
    int mods = method.getModifiers();
    return Modifier.isPublic(mods) && Modifier.isStatic(mods) ? method : null;
  }

  @Override
  public String toString() {
    return methodName + "(String)";
  }

}
