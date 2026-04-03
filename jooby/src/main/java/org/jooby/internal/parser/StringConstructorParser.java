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

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.jooby.Parser;

import com.google.inject.TypeLiteral;

public class StringConstructorParser implements Parser {

  public boolean matches(final TypeLiteral<?> toType) {
    try {
      return constructor(toType.getRawType()) != null;
    } catch (NoSuchMethodException x) {
      return false;
    }
  }

  @Override
  public Object parse(final TypeLiteral<?> type, final Parser.Context ctx) throws Exception {
    return ctx.param(params -> {
      try {
        return constructor(type.getRawType()).newInstance(params.get(0));
      } catch (NoSuchMethodException x) {
        return ctx.next();
      }
    });
  }

  @Override
  public String toString() {
    return "init(String)";
  }

  public static Object parse(final TypeLiteral<?> type, final Object data) throws Exception {
    return constructor(type.getRawType()).newInstance(data);
  }

  private static Constructor<?> constructor(final Class<?> rawType) throws NoSuchMethodException {
    Constructor<?> constructor = rawType.getDeclaredConstructor(String.class);
    return Modifier.isPublic(constructor.getModifiers()) ? constructor : null;
  }

}
