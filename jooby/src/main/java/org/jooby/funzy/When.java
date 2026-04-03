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
package org.jooby.funzy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Functional idiom for switch/case statement.
 *
 * Basic example:
 * <pre>{code@
 *   import static org.jooby.funzy.When.when;
 *
 *   Object value = ...;
 *   String result = when(value)
 *     .is(Number.class, "Got a number")
 *     .is(String.class, "Got a string")
 *     .orElse("Unknown");
 *   System.out.println(result);
 * }</pre>
 *
 * Automatic cast example:
 *
 * <pre>{@code
 *   import static org.jooby.funzy.When.when;
 *
 *   Object value = ...;
 *   int result = when(value)
 *     .is(Integer.class, i -> i * 2)
 *     .orElse(-1);
 *
 *   System.out.println(result);
 * }</pre>
 *
 *
 * @param <V> Input type.
 */
public class When<V> {

  public static class Value<V, R> {
    private final V source;
    private final Map<Throwing.Predicate, Throwing.Function> predicates = new LinkedHashMap<>();

    private Value(final V source) {
      this.source = source;
    }

    public Value<V, R> is(V value, R result) {
      return is(source -> Objects.equals(source, value), v -> result);
    }

    public <T extends V> Value<V, R> is(Class<T> predicate, R result) {
      return is(predicate::isInstance, v -> result);
    }

    public Value<V, R> is(V value, Throwing.Supplier<R> result) {
      return is(source -> Objects.equals(source, value), v -> result.get());
    }

    public <T extends V> Value<V, R> is(Class<T> predicate, Throwing.Function<T, R> result) {
      return is(predicate::isInstance, result);
    }

    public <T extends V> Value<V, R> is(Throwing.Predicate<T> predicate,
      Throwing.Supplier<R> result) {
      return is(predicate, v -> result.get());
    }

    public <T extends V> Value<V, R> is(Throwing.Predicate<T> predicate,
      Throwing.Function<T, R> result) {
      predicates.put(predicate, result);
      return this;
    }

    public R get() {
      return toOptional().orElseThrow(NoSuchElementException::new);
    }

    public R orElse(R value) {
      return toOptional().orElse(value);
    }

    public R orElseGet(Throwing.Supplier<R> value) {
      return toOptional().orElseGet(value);
    }

    public R orElseThrow(Throwing.Supplier<Throwable> exception) {
      return toOptional().orElseThrow(() -> Throwing.sneakyThrow(exception.get()));
    }

    public Optional<R> toOptional() {
      for (Map.Entry<Throwing.Predicate, Throwing.Function> predicate : predicates.entrySet()) {
        if (predicate.getKey().test(source)) {
          return Optional.ofNullable( (R) predicate.getValue().apply(source));
        }
      }
      return Optional.empty();
    }
  }

  private final V source;

  public When(final V source) {
    this.source = source;
  }

  public final static <V> When<V> when(V value) {
    return new When<>(value);
  }

  public <R> Value<V, R> is(V value, R result) {
    Value<V, R> when = new Value<>(source);
    when.is(value, result);
    return when;
  }

  public <T extends V, R> Value<V, R> is(Class<T> predicate, R result) {
    Value<V, R> when = new Value<>(source);
    when.is(predicate, result);
    return when;
  }

  public <R> Value<V, R> is(V value, Throwing.Supplier<R> result) {
    Value<V, R> when = new Value<>(source);
    when.is(value, result);
    return when;
  }

  public <T extends V, R> Value<V, R> is(Class<T> predicate, Throwing.Function<T, R> result) {
    Value<V, R> when = new Value<>(source);
    when.is(predicate, result);
    return when;
  }

  public <T extends V, R> Value<V, R> is(Throwing.Predicate<T> predicate,
    Throwing.Supplier<R> result) {
    Value<V, R> when = new Value<>(source);
    when.is(predicate, result);
    return when;
  }

  public <T extends V, R> Value<V, R> is(Throwing.Predicate<T> predicate,
    Throwing.Function<T, R> result) {
    Value<V, R> when = new Value<>(source);
    when.is(predicate, result);
    return when;
  }

}
