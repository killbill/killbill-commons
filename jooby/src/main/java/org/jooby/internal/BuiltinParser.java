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

import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

import org.jooby.Parser;

import com.google.inject.TypeLiteral;

@SuppressWarnings({"unchecked", "rawtypes" })
public enum BuiltinParser implements Parser {

  Basic {
    private final Map<Class<?>, Function<String, Object>> parsers = Map.ofEntries(
        Map.entry(BigDecimal.class, NOT_EMPTY.andThen(BigDecimal::new)),
        Map.entry(BigInteger.class, NOT_EMPTY.andThen(BigInteger::new)),
        Map.entry(Byte.class, NOT_EMPTY.andThen(Byte::valueOf)),
        Map.entry(byte.class, NOT_EMPTY.andThen(Byte::valueOf)),
        Map.entry(Double.class, NOT_EMPTY.andThen(Double::valueOf)),
        Map.entry(double.class, NOT_EMPTY.andThen(Double::valueOf)),
        Map.entry(Float.class, NOT_EMPTY.andThen(Float::valueOf)),
        Map.entry(float.class, NOT_EMPTY.andThen(Float::valueOf)),
        Map.entry(Integer.class, NOT_EMPTY.andThen(Integer::valueOf)),
        Map.entry(int.class, NOT_EMPTY.andThen(Integer::valueOf)),
        Map.entry(Long.class, NOT_EMPTY.andThen(this::toLong)),
        Map.entry(long.class, NOT_EMPTY.andThen(this::toLong)),
        Map.entry(Short.class, NOT_EMPTY.andThen(Short::valueOf)),
        Map.entry(short.class, NOT_EMPTY.andThen(Short::valueOf)),
        Map.entry(Boolean.class, NOT_EMPTY.andThen(this::toBoolean)),
        Map.entry(boolean.class, NOT_EMPTY.andThen(this::toBoolean)),
        Map.entry(Character.class, NOT_EMPTY.andThen(this::toCharacter)),
        Map.entry(char.class, NOT_EMPTY.andThen(this::toCharacter)),
        Map.entry(String.class, (Function<String, Object>) this::toString)
    );

    @Override
    public Object parse(final TypeLiteral<?> type, final Parser.Context ctx) throws Throwable {
      Function<String, Object> parser = parsers.get(type.getRawType());
      if (parser != null) {
        return ctx
            .param(values -> parser.apply(values.get(0))).body(body -> parser.apply(body.text()));
      }
      return ctx.next();
    }

    private String toString(final String value) {
      return value;
    }

    private char toCharacter(final String value) {
      return value.charAt(0);
    }

    private Boolean toBoolean(final String value) {
      if ("true".equals(value)) {
        return Boolean.TRUE;
      } else if ("false".equals(value)) {
        return Boolean.FALSE;
      }
      throw new IllegalArgumentException("Not a boolean: " + value);
    }

    private Long toLong(final String value) {
      try {
        return Long.valueOf(value);
      } catch (NumberFormatException ex) {
        // long as date, like If-Modified-Since
        try {
          LocalDateTime date = LocalDateTime.parse(value, Headers.fmt);
          Instant instant = date.toInstant(ZoneOffset.UTC);
          return instant.toEpochMilli();
        } catch (DateTimeParseException ignored) {
          throw ex;
        }
      }

    }
  },

  Collection {
    private static final Set<Class<?>> SUPPORTED = Set.of(List.class, Set.class, SortedSet.class);

    private boolean matches(final TypeLiteral<?> toType) {
      return SUPPORTED.contains(toType.getRawType())
          && toType.getType() instanceof ParameterizedType;
    }

    @Override
    public Object parse(final TypeLiteral<?> type, final Parser.Context ctx) throws Throwable {
      if (matches(type)) {
        return ctx.param(values -> {
          Class<?> rawType = type.getRawType();
          java.util.Collection result;
          if (SortedSet.class.isAssignableFrom(rawType)) {
            result = new TreeSet<>();
          } else if (Set.class.isAssignableFrom(rawType)) {
            result = new java.util.LinkedHashSet<>();
          } else {
            result = new ArrayList<>();
          }
          TypeLiteral<?> paramType = TypeLiteral.get(((ParameterizedType) type.getType())
              .getActualTypeArguments()[0]);
          for (Object value : values) {
            result.add(ctx.next(paramType, value));
          }
          if (SortedSet.class.isAssignableFrom(rawType)) {
            return Collections.unmodifiableSortedSet((SortedSet) result);
          } else if (Set.class.isAssignableFrom(rawType)) {
            return Collections.unmodifiableSet((Set) result);
          } else {
            return Collections.unmodifiableList((List) result);
          }
        });
      } else {
        return ctx.next();
      }
    }
  },

  Optional {
    private boolean matches(final TypeLiteral<?> toType) {
      return Optional.class == toType.getRawType() && toType.getType() instanceof ParameterizedType;
    }

    @Override
    public Object parse(final TypeLiteral<?> type, final Parser.Context ctx)
        throws Throwable {
      if (matches(type)) {
        TypeLiteral<?> paramType = TypeLiteral.get(((ParameterizedType) type.getType())
            .getActualTypeArguments()[0]);
        return ctx
            .param(values -> {
              if (values.size() == 0) {
                return java.util.Optional.empty();
              }
              return java.util.Optional.of(ctx.next(paramType));
            }).body(body -> {
              if (body.length() == 0) {
                return java.util.Optional.empty();
              }
              return java.util.Optional.of(ctx.next(paramType));
            });
      } else {
        return ctx.next();
      }
    }
  },

  Enum {
    @Override
    public Object parse(final TypeLiteral<?> type, final Parser.Context ctx)
        throws Throwable {
      Class rawType = type.getRawType();
      if (Enum.class.isAssignableFrom(rawType)) {
        return ctx
            .param(values -> toEnum(rawType, values.get(0)))
            .body(body -> toEnum(rawType, body.text()));
      } else {
        return ctx.next();
      }
    }

    Object toEnum(final Class type, final String value) {
      Set<Enum> set = EnumSet.allOf(type);
      return set.stream()
          .filter(e -> e.name().equalsIgnoreCase(value))
          .findFirst()
          .orElseGet(() -> java.lang.Enum.valueOf(type, value));
    }
  },

  Bytes {
    @Override
    public Object parse(final TypeLiteral<?> type, final Parser.Context ctx) throws Throwable {
      if (byte[].class.equals(type.getRawType())) {
        return ctx.body(body -> body.bytes());
      }
      return ctx.next();
    }

    @Override
    public String toString() {
      return "byte[]";
    }
  }

}
