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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Collection of throwable interfaces to simplify exception handling, specially on lambdas.
 *
 * We do provide throwable and 100% compatible implementation of {@link java.util.function.Function},
 * {@link java.util.function.Consumer}, {@link java.lang.Runnable},
 * {@link java.util.function.Supplier}, {@link java.util.function.Predicate} and
 * {@link java.util.function.BiPredicate}.
 *
 * Examples:
 *
 * <pre>{@code
 *
 *  interface Query {
 *    Item findById(String id) throws IOException;
 *  }
 *
 *  Query query = ...
 *
 *  List<Item> items = Arrays.asList("1", "2", "3")
 *    .stream()
 *    .map(throwingFunction(query::findById))
 *    .collect(Collectors.toList());
 *
 * }</pre>
 *
 *
 * @author edgar
 * @since 0.1.0
 */
public class Throwing {
  private interface Memoized {
  }

  /**
   * Throwable version of {@link Predicate}.
   *
   * @param <V> Input type.
   */
  public interface Predicate<V> extends java.util.function.Predicate<V> {
    boolean tryTest(V v) throws Throwable;

    @Override default boolean test(V v) {
      try {
        return tryTest(v);
      } catch (Throwable x) {
        throw sneakyThrow(x);
      }
    }
  }

  /**
   * Throwable version of {@link Predicate}.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   */
  public interface Predicate2<V1, V2> extends java.util.function.BiPredicate<V1, V2> {
    boolean tryTest(V1 v1, V2 v2) throws Throwable;

    @Override default boolean test(V1 v1, V2 v2) {
      try {
        return tryTest(v1, v2);
      } catch (Throwable x) {
        throw sneakyThrow(x);
      }
    }
  }

  /**
   * Throwable version of {@link java.lang.Runnable}.
   */
  @FunctionalInterface
  public interface Runnable extends java.lang.Runnable {
    void tryRun() throws Throwable;

    @Override default void run() {
      runAction(this);
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param action Action to execute.
     * @return A new consumer with a listener action.
     */
    default Runnable onFailure(java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param type Exception type filter.
     * @param action Action to execute.
     * @param <X> Exception type.
     * @return A new consumer with a listener action.
     */
    default <X extends Throwable> Runnable onFailure(Class<? extends X> type,
        java.util.function.Consumer<X> action) {
      return () -> runOnFailure(() -> tryRun(), type, action);
    }

    /**
     * Wrap an exception as new exception provided by the given wrap function.
     *
     * @param wrapper Wrap function.
     * @return A new consumer.
     */
    default Runnable wrap(java.util.function.Function<Throwable, Exception> wrapper) {
      return () -> runWrap(() -> tryRun(), wrapper);
    }

    /**
     * Unwrap an exception and rethrow. Useful to produce clean/shorter stacktraces.
     *
     * @param type Type to unwrap.
     * @param <X> Exception type.
     * @return A new consumer.
     */
    default <X extends Throwable> Runnable unwrap(Class<? extends X> type) {
      return () -> runUnwrap(() -> tryRun(), type);
    }
  }

  /**
   * Throwable version of {@link java.util.function.Supplier}.
   *
   * @param <V> Result type.
   */
  @FunctionalInterface
  public interface Supplier<V> extends java.util.function.Supplier<V> {

    V tryGet() throws Throwable;

    @Override default V get() {
      return fn(this);
    }

    /**
     * Apply this function and run the given action in case of exception.
     *
     * @param action Action to run when exception occurs.
     * @return A new function.
     */
    default Supplier<V> onFailure(java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     *
     * Apply this function and run the given action in case of exception.
     *
     * @param type Exception filter.
     * @param action Action to run when exception occurs.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Supplier<V> onFailure(Class<X> type,
        java.util.function.Consumer<X> action) {
      return () -> fnOnFailure(() -> tryGet(), type, action);
    }

    /**
     * Apply this function and wrap any resulting exception.
     *
     * @param wrapper Exception wrapper.
     * @return A new function.
     */
    default Supplier<V> wrap(java.util.function.Function<Throwable, Exception> wrapper) {
      return () -> fnWrap(() -> tryGet(), wrapper);
    }

    /**
     * Apply this function and unwrap any resulting exception. Useful to get clean/shorter stacktrace.
     *
     * @param type Exception to unwrap.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Supplier<V> unwrap(Class<? extends X> type) {
      return () -> fnUnwrap(() -> tryGet(), type);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Supplier<V> orElse(V defaultValue) {
      return orElse(() -> defaultValue);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Supplier<V> orElse(Supplier<V> defaultValue) {
      return () -> fn(() -> tryGet(), defaultValue);
    }

    /**
     * Apply this function or recover from it in case of exception.
     *
     * @param fn Exception recover.
     * @return A new function.
     */
    default Supplier<V> recover(java.util.function.Function<Throwable, V> fn) {
      return recover(Throwable.class, fn);
    }

    /**
     * Apply this function or recover from a specific exception in case of exception.
     *
     * @param type Exception filter.
     * @param fn Exception recover.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Supplier<V> recover(Class<? extends X> type,
        java.util.function.Function<X, V> fn) {
      return () -> fnRecover(() -> tryGet(), type, fn);
    }

    /**
     * Singleton version of this supplier.
     *
     * @return A memo function.
     */
    default Supplier<V> singleton() {
      if (this instanceof Memoized) {
        return this;
      }
      AtomicReference<V> ref = new AtomicReference<>();
      return (Supplier<V> & Memoized) () -> {
        if (ref.get() == null) {
          ref.set(tryGet());
        }
        return ref.get();
      };
    }
  }

  /**
   * Throwable version of {@link java.util.function.Consumer}.
   *
   * This class rethrow any exception using the {@link #sneakyThrow(Throwable)} technique.
   *
   * @param <V> Input type.
   */
  @FunctionalInterface
  public interface Consumer<V> extends java.util.function.Consumer<V> {
    /**
     * Performs this operation on the given argument.
     *
     * @param value Argument.
     * @throws Throwable If something goes wrong.
     */
    void tryAccept(V value) throws Throwable;

    @Override default void accept(V v) {
      runAction(() -> tryAccept(v));
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param action Action to execute.
     * @param <V1> Input type.
     * @return A new consumer with a listener action.
     */
    default <V1 extends V> Consumer<V1> onFailure(java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param type Exception type filter.
     * @param action Action to execute.
     * @param <X> Exception type.
     * @param <V1> Input type.
     * @return A new consumer with a listener action.
     */
    default <V1 extends V, X extends Throwable> Consumer<V1> onFailure(Class<? extends X> type,
        java.util.function.Consumer<X> action) {
      return value -> runOnFailure(() -> tryAccept(value), type, action);
    }

    /**
     * Wrap an exception as new exception provided by the given wrap function.
     *
     * @param wrapper Wrap function.
     * @param <V1> Input type
     * @return A new consumer.
     */
    default <V1 extends V> Consumer<V1> wrap(
        java.util.function.Function<Throwable, Exception> wrapper) {
      return value -> runWrap(() -> tryAccept(value), wrapper);
    }

    /**
     * Unwrap an exception and rethrow. Useful to produce clean/shorter stacktraces.
     *
     * @param type Type to unwrap.
     * @param <V1> Input type
     * @param <X> Exception type.
     * @return A new consumer.
     */
    default <V1 extends V, X extends Throwable> Consumer<V1> unwrap(Class<? extends X> type) {
      return value -> runUnwrap(() -> tryAccept(value), type);
    }
  }

  /**
   * Two argument version of {@link Consumer}.
   *
   * This class rethrow any exception using the {@link #sneakyThrow(Throwable)} technique.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   */
  @FunctionalInterface
  public interface Consumer2<V1, V2> {
    /**
     * Performs this operation on the given argument.
     *
     * @param v1 Argument.
     * @param v2 Argument.
     * @throws Throwable If something goes wrong.
     */
    void tryAccept(V1 v1, V2 v2) throws Throwable;

    default void accept(V1 v1, V2 v2) {
      runAction(() -> tryAccept(v1, v2));
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param action Action to execute.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @return A new consumer with a listener action.
     */
    default <TV1 extends V1, TV2 extends V2> Consumer2<TV1, TV2> onFailure(
        java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param type Exception type filter.
     * @param action Action to execute.
     * @param <X> Exception type.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @return A new consumer with a listener action.
     */
    default <TV1 extends V1, TV2 extends V2, X extends Throwable> Consumer2<TV1, TV2> onFailure(
        Class<? extends X> type, java.util.function.Consumer<X> action) {
      return (v1, v2) -> runOnFailure(() -> tryAccept(v1, v2), type, action);
    }

    /**
     * Wrap an exception as new exception provided by the given wrap function.
     *
     * @param wrapper Wrap function.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @return A new consumer.
     */
    default <TV1 extends V1, TV2 extends V2> Consumer2<TV1, TV2> wrap(
        java.util.function.Function<Throwable, Exception> wrapper) {
      return (v1, v2) -> runWrap(() -> tryAccept(v1, v2), wrapper);
    }

    /**
     * Unwrap an exception and rethrow. Useful to produce clean/shorter stacktraces.
     *
     * @param type Type to unwrap.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <X> Exception type.
     * @return A new consumer.
     */
    default <TV1 extends V1, TV2 extends V2, X extends Throwable> Consumer2<TV1, TV2> unwrap(
        Class<X> type) {
      return (v1, v2) -> runUnwrap(() -> tryAccept(v1, v2), type);
    }
  }

  /**
   * Three argument version of {@link Consumer}.
   *
   * This class rethrow any exception using the {@link #sneakyThrow(Throwable)} technique.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   * @param <V3> Input type.
   */
  @FunctionalInterface
  public interface Consumer3<V1, V2, V3> {
    /**
     * Performs this operation on the given argument.
     *
     * @param v1 Argument.
     * @param v2 Argument.
     * @param v3 Argument.
     * @throws Throwable If something goes wrong.
     */
    void tryAccept(V1 v1, V2 v2, V3 v3) throws Throwable;

    default void accept(V1 v1, V2 v2, V3 v3) {
      runAction(() -> tryAccept(v1, v2, v3));
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param action Action to execute.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @return A new consumer with a listener action.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3> Consumer3<TV1, TV2, TV3> onFailure(
        java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param type Exception type filter.
     * @param action Action to execute.
     * @param <X> Exception type.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @return A new consumer with a listener action.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, X extends Throwable> Consumer3<TV1, TV2, TV3> onFailure(
        Class<? extends X> type, java.util.function.Consumer<X> action) {
      return (v1, v2, v3) -> runOnFailure(() -> tryAccept(v1, v2, v3), type, action);
    }

    /**
     * Wrap an exception as new exception provided by the given wrap function.
     *
     * @param wrapper Wrap function.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @return A new consumer.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3> Consumer3<TV1, TV2, TV3> wrap(
        java.util.function.Function<Throwable, Exception> wrapper) {
      return (v1, v2, v3) -> runWrap(() -> tryAccept(v1, v2, v3), wrapper);
    }

    /**
     * Unwrap an exception and rethrow. Useful to produce clean/shorter stacktraces.
     *
     * @param type Type to unwrap.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <X> Exception type.
     * @return A new consumer.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, X extends Throwable> Consumer3<TV1, TV2, TV3> unwrap(
        Class<X> type) {
      return (v1, v2, v3) -> runUnwrap(() -> tryAccept(v1, v2, v3), type);
    }
  }

  /**
   * Four argument version of {@link Consumer}.
   *
   * This class rethrow any exception using the {@link #sneakyThrow(Throwable)} technique.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   * @param <V3> Input type.
   * @param <V4> Input type.
   */
  @FunctionalInterface
  public interface Consumer4<V1, V2, V3, V4> {
    /**
     * Performs this operation on the given arguments.
     *
     * @param v1 Argument.
     * @param v2 Argument.
     * @param v3 Argument.
     * @param v4 Argument.
     * @throws Throwable If something goes wrong.
     */
    void tryAccept(V1 v1, V2 v2, V3 v3, V4 v4) throws Throwable;

    default void accept(V1 v1, V2 v2, V3 v3, V4 v4) {
      runAction(() -> tryAccept(v1, v2, v3, v4));
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param action Action to execute.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @return A new consumer with a listener action.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4> Consumer4<TV1, TV2, TV3, TV4> onFailure(
        java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param type Exception type filter.
     * @param action Action to execute.
     * @param <X> Exception type.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @return A new consumer with a listener action.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, X extends Throwable> Consumer4<TV1, TV2, TV3, TV4> onFailure(
        Class<? extends X> type, java.util.function.Consumer<X> action) {
      return (v1, v2, v3, v4) -> runOnFailure(() -> tryAccept(v1, v2, v3, v4), type, action);
    }

    /**
     * Wrap an exception as new exception provided by the given wrap function.
     *
     * @param wrapper Wrap function.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @return A new consumer.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4> Consumer4<TV1, TV2, TV3, TV4> wrap(
        java.util.function.Function<Throwable, Exception> wrapper) {
      return (v1, v2, v3, v4) -> runWrap(() -> tryAccept(v1, v2, v3, v4), wrapper);
    }

    /**
     * Unwrap an exception and rethrow. Useful to produce clean/shorter stacktraces.
     *
     * @param type Type to unwrap.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <X> Exception type.
     * @return A new consumer.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, X extends Throwable> Consumer4<TV1, TV2, TV3, TV4> unwrap(
        Class<X> type) {
      return (v1, v2, v3, v4) -> runUnwrap(() -> tryAccept(v1, v2, v3, v4), type);
    }
  }

  /**
   * Five argument version of {@link Consumer}.
   *
   * This class rethrow any exception using the {@link #sneakyThrow(Throwable)} technique.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   * @param <V3> Input type.
   * @param <V4> Input type.
   * @param <V5> Input type.
   */
  @FunctionalInterface
  public interface Consumer5<V1, V2, V3, V4, V5> {
    /**
     * Performs this operation on the given arguments.
     *
     * @param v1 Argument.
     * @param v2 Argument.
     * @param v3 Argument.
     * @param v4 Argument.
     * @param v5 Argument.
     * @throws Throwable If something goes wrong.
     */
    void tryAccept(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5) throws Throwable;

    default void accept(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5) {
      runAction(() -> tryAccept(v1, v2, v3, v4, v5));
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param action Action to execute.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @return A new consumer with a listener action.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5> Consumer5<TV1, TV2, TV3, TV4, TV5> onFailure(
        java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param type Exception type filter.
     * @param action Action to execute.
     * @param <X> Exception type.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @return A new consumer with a listener action.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5, X extends Throwable> Consumer5<TV1, TV2, TV3, TV4, TV5> onFailure(
        Class<? extends X> type, java.util.function.Consumer<X> action) {
      return (v1, v2, v3, v4, v5) -> runOnFailure(() -> tryAccept(v1, v2, v3, v4, v5), type,
          action);
    }

    /**
     * Wrap an exception as new exception provided by the given wrap function.
     *
     * @param wrapper Wrap function.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @return A new consumer.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5> Consumer5<TV1, TV2, TV3, TV4, TV5> wrap(
        java.util.function.Function<Throwable, Exception> wrapper) {
      return (v1, v2, v3, v4, v5) -> runWrap(() -> tryAccept(v1, v2, v3, v4, v5), wrapper);
    }

    /**
     * Unwrap an exception and rethrow. Useful to produce clean/shorter stacktraces.
     *
     * @param type Type to unwrap.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @param <X> Exception type.
     * @return A new consumer.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5, X extends Throwable> Consumer5<TV1, TV2, TV3, TV4, TV5> unwrap(
        Class<X> type) {
      return (v1, v2, v3, v4, v5) -> runUnwrap(() -> tryAccept(v1, v2, v3, v4, v5), type);
    }
  }

  /**
   * Six argument version of {@link Consumer}.
   *
   * This class rethrow any exception using the {@link #sneakyThrow(Throwable)} technique.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   * @param <V3> Input type.
   * @param <V4> Input type.
   * @param <V5> Input type.
   * @param <V6> Input type.
   */
  @FunctionalInterface
  public interface Consumer6<V1, V2, V3, V4, V5, V6> {
    /**
     * Performs this operation on the given arguments.
     *
     * @param v1 Argument.
     * @param v2 Argument.
     * @param v3 Argument.
     * @param v4 Argument.
     * @param v5 Argument.
     * @param v6 Argument.
     * @throws Throwable If something goes wrong.
     */
    void tryAccept(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5, V6 v6) throws Throwable;

    /**
     * Performs this operation on the given arguments and throw any exception using {@link #sneakyThrow(Throwable)} method.
     *
     * @param v1 Argument.
     * @param v2 Argument.
     * @param v3 Argument.
     * @param v4 Argument.
     * @param v5 Argument.
     * @param v6 Argument.
     */
    default void accept(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5, V6 v6) {
      runAction(() -> tryAccept(v1, v2, v3, v4, v5, v6));
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param action Action to execute.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @param <TV6> Input type.
     * @return A new consumer with a listener action.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5, TV6 extends V6> Consumer6<TV1, TV2, TV3, TV4, TV5, TV6> onFailure(
        java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param type Exception type filter.
     * @param action Action to execute.
     * @param <X> Exception type.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @param <TV6> Input type.
     * @return A new consumer with a listener action.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5, TV6 extends V6, X extends Throwable> Consumer6<TV1, TV2, TV3, TV4, TV5, TV6> onFailure(
        Class<? extends X> type, java.util.function.Consumer<X> action) {
      return (v1, v2, v3, v4, v5, v6) -> runOnFailure(() -> tryAccept(v1, v2, v3, v4, v5, v6), type,
          action);
    }

    /**
     * Wrap an exception as new exception provided by the given wrap function.
     *
     * @param wrapper Wrap function.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @param <TV6> Input type.
     * @return A new consumer.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5, TV6 extends V6> Consumer6<TV1, TV2, TV3, TV4, TV5, TV6> wrap(
        java.util.function.Function<Throwable, Exception> wrapper) {
      return (v1, v2, v3, v4, v5, v6) -> runWrap(() -> tryAccept(v1, v2, v3, v4, v5, v6), wrapper);
    }

    /**
     * Unwrap an exception and rethrow. Useful to produce clean/shorter stacktraces.
     *
     * @param type Type to unwrap.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @param <TV6> Input type.
     * @param <X> Exception type.
     * @return A new consumer.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5, TV6 extends V6, X extends Throwable> Consumer6<TV1, TV2, TV3, TV4, TV5, TV6> unwrap(
        Class<X> type) {
      return (v1, v2, v3, v4, v5, v6) -> runUnwrap(() -> tryAccept(v1, v2, v3, v4, v5, v6), type);
    }
  }

  /**
   * Seven argument version of {@link Consumer}.
   *
   * This class rethrow any exception using the {@link #sneakyThrow(Throwable)} technique.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   * @param <V3> Input type.
   * @param <V4> Input type.
   * @param <V5> Input type.
   * @param <V6> Input type.
   * @param <V7> Input type.
   */
  @FunctionalInterface
  public interface Consumer7<V1, V2, V3, V4, V5, V6, V7> {
    /**
     * Performs this operation on the given arguments.
     *
     * @param v1 Argument.
     * @param v2 Argument.
     * @param v3 Argument.
     * @param v4 Argument.
     * @param v5 Argument.
     * @param v6 Argument.
     * @param v7 Argument.
     * @throws Throwable If something goes wrong.
     */
    void tryAccept(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5, V6 v6, V7 v7) throws Throwable;

    /**
     * Performs this operation on the given arguments and throw any exception using {@link #sneakyThrow(Throwable)} method.
     *
     * @param v1 Argument.
     * @param v2 Argument.
     * @param v3 Argument.
     * @param v4 Argument.
     * @param v5 Argument.
     * @param v6 Argument.
     * @param v7 Argument.
     */
    default void accept(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5, V6 v6, V7 v7) {
      runAction(() -> tryAccept(v1, v2, v3, v4, v5, v6, v7));
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param action Action to execute.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @param <TV6> Input type.
     * @param <TV7> Input type.
     * @return A new consumer with a listener action.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5, TV6 extends V6, TV7 extends V7> Consumer7<TV1, TV2, TV3, TV4, TV5, TV6, TV7> onFailure(
        java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param type Exception type filter.
     * @param action Action to execute.
     * @param <X> Exception type.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @param <TV6> Input type.
     * @param <TV7> Input type.
     * @return A new consumer with a listener action.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5, TV6 extends V6, TV7 extends V7, X extends Throwable> Consumer7<TV1, TV2, TV3, TV4, TV5, TV6, TV7> onFailure(
        Class<? extends X> type, java.util.function.Consumer<X> action) {
      return (v1, v2, v3, v4, v5, v6, v7) -> runOnFailure(
          () -> tryAccept(v1, v2, v3, v4, v5, v6, v7), type, action);
    }

    /**
     * Wrap an exception as new exception provided by the given wrap function.
     *
     * @param wrapper Wrap function.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @param <TV6> Input type.
     * @param <TV7> Input type.
     * @return A new consumer.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5, TV6 extends V6, TV7 extends V7> Consumer7<TV1, TV2, TV3, TV4, TV5, TV6, TV7> wrap(
        java.util.function.Function<Throwable, Exception> wrapper) {
      return (v1, v2, v3, v4, v5, v6, v7) -> runWrap(() -> tryAccept(v1, v2, v3, v4, v5, v6, v7),
          wrapper);
    }

    /**
     * Unwrap an exception and rethrow. Useful to produce clean/shorter stacktraces.
     *
     * @param type Type to unwrap.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @param <TV6> Input type.
     * @param <TV7> Input type.
     * @param <X> Exception type.
     * @return A new consumer.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5, TV6 extends V6, TV7 extends V7, X extends Throwable> Consumer7<TV1, TV2, TV3, TV4, TV5, TV6, TV7> unwrap(
        Class<X> type) {
      return (v1, v2, v3, v4, v5, v6, v7) -> runUnwrap(() -> tryAccept(v1, v2, v3, v4, v5, v6, v7),
          type);
    }
  }

  /**
   * Seven argument version of {@link Consumer}.
   *
   * This class rethrow any exception using the {@link #sneakyThrow(Throwable)} technique.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   * @param <V3> Input type.
   * @param <V4> Input type.
   * @param <V5> Input type.
   * @param <V6> Input type.
   * @param <V7> Input type.
   * @param <V8> Input type.
   */
  @FunctionalInterface
  public interface Consumer8<V1, V2, V3, V4, V5, V6, V7, V8> {
    /**
     * Performs this operation on the given arguments.
     *
     * @param v1 Argument.
     * @param v2 Argument.
     * @param v3 Argument.
     * @param v4 Argument.
     * @param v5 Argument.
     * @param v6 Argument.
     * @param v7 Argument.
     * @param v8 Argument.
     * @throws Throwable If something goes wrong.
     */
    void tryAccept(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5, V6 v6, V7 v7, V8 v8) throws Throwable;

    /**
     * Performs this operation on the given arguments and throw any exception using {@link #sneakyThrow(Throwable)} method.
     *
     * @param v1 Argument.
     * @param v2 Argument.
     * @param v3 Argument.
     * @param v4 Argument.
     * @param v5 Argument.
     * @param v6 Argument.
     * @param v7 Argument.
     * @param v8 Argument.
     */
    default void accept(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5, V6 v6, V7 v7, V8 v8) {
      runAction(() -> tryAccept(v1, v2, v3, v4, v5, v6, v7, v8));
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param action Action to execute.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @param <TV6> Input type.
     * @param <TV7> Input type.
     * @param <TV8> Input type.
     * @return A new consumer with a listener action.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5, TV6 extends V6, TV7 extends V7, TV8 extends V8> Consumer8<TV1, TV2, TV3, TV4, TV5, TV6, TV7, TV8> onFailure(
        java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     * Execute the given action before throwing the exception.
     *
     * @param type Exception type filter.
     * @param action Action to execute.
     * @param <X> Exception type.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @param <TV6> Input type.
     * @param <TV7> Input type.
     * @param <TV8> Input type.
     * @return A new consumer with a listener action.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5, TV6 extends V6, TV7 extends V7, TV8 extends V8, X extends Throwable> Consumer8<TV1, TV2, TV3, TV4, TV5, TV6, TV7, TV8> onFailure(
        Class<? extends X> type, java.util.function.Consumer<X> action) {
      return (v1, v2, v3, v4, v5, v6, v7, v8) -> runOnFailure(
          () -> tryAccept(v1, v2, v3, v4, v5, v6, v7, v8), type, action);
    }

    /**
     * Wrap an exception as new exception provided by the given wrap function.
     *
     * @param wrapper Wrap function.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @param <TV6> Input type.
     * @param <TV7> Input type.
     * @param <TV8> Input type.
     * @return A new consumer.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5, TV6 extends V6, TV7 extends V7, TV8 extends V8> Consumer8<TV1, TV2, TV3, TV4, TV5, TV6, TV7, TV8> wrap(
        java.util.function.Function<Throwable, Exception> wrapper) {
      return (v1, v2, v3, v4, v5, v6, v7, v8) -> runWrap(
          () -> tryAccept(v1, v2, v3, v4, v5, v6, v7, v8), wrapper);
    }

    /**
     * Unwrap an exception and rethrow. Useful to produce clean/shorter stacktraces.
     *
     * @param type Type to unwrap.
     * @param <TV1> Input type.
     * @param <TV2> Input type.
     * @param <TV3> Input type.
     * @param <TV4> Input type.
     * @param <TV5> Input type.
     * @param <TV6> Input type.
     * @param <TV7> Input type.
     * @param <TV8> Input type.
     * @param <X> Exception type.
     * @return A new consumer.
     */
    default <TV1 extends V1, TV2 extends V2, TV3 extends V3, TV4 extends V4, TV5 extends V5, TV6 extends V6, TV7 extends V7, TV8 extends V8, X extends Throwable> Consumer8<TV1, TV2, TV3, TV4, TV5, TV6, TV7, TV8> unwrap(
        Class<X> type) {
      return (v1, v2, v3, v4, v5, v6, v7, v8) -> runUnwrap(
          () -> tryAccept(v1, v2, v3, v4, v5, v6, v7, v8), type);
    }
  }

  /**
   * Throwable version of {@link java.util.function.Function}.
   *
   * The {@link #apply(Object)} method throws checked exceptions using {@link #sneakyThrow(Throwable)} method.
   *
   * @param <V> Input type.
   * @param <R> Output type.
   */
  @FunctionalInterface
  public interface Function<V, R> extends java.util.function.Function<V, R> {
    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param value Input argument.
     * @return Result.
     * @throws Throwable If something goes wrong.
     */
    R tryApply(V value) throws Throwable;

    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v Input argument.
     * @return Result.
     */
    @Override default R apply(V v) {
      return fn(() -> tryApply(v));
    }

    /**
     * Apply this function and run the given action in case of exception.
     *
     * @param action Action to run when exception occurs.
     * @return A new function.
     */
    default Function<V, R> onFailure(java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     *
     * Apply this function and run the given action in case of exception.
     *
     * @param type Exception filter.
     * @param action Action to run when exception occurs.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function<V, R> onFailure(Class<X> type,
        java.util.function.Consumer<X> action) {
      return value -> fnOnFailure(() -> tryApply(value), type, action);
    }

    /**
     * Apply this function and wrap any resulting exception.
     *
     * @param wrapper Exception wrapper.
     * @return A new function.
     */
    default Function<V, R> wrap(java.util.function.Function<Throwable, Exception> wrapper) {
      return value -> fnWrap(() -> tryApply(value), wrapper);
    }

    /**
     * Apply this function and unwrap any resulting exception. Useful to get clean/shorter stacktrace.
     *
     * @param type Exception to unwrap.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function<V, R> unwrap(Class<? extends X> type) {
      return value -> fnUnwrap(() -> tryApply(value), type);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function<V, R> orElse(R defaultValue) {
      return orElse(() -> defaultValue);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function<V, R> orElse(Supplier<R> defaultValue) {
      return value -> fn(() -> tryApply(value), defaultValue);
    }

    /**
     * Apply this function or recover from it in case of exception.
     *
     * @param fn Exception recover.
     * @return A new function.
     */
    default Function<V, R> recover(java.util.function.Function<Throwable, R> fn) {
      return recover(Throwable.class, fn);
    }

    /**
     * Apply this function or recover from a specific exception in case of exception.
     *
     * @param type Exception filter.
     * @param fn Exception recover.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function<V, R> recover(Class<? extends X> type,
        java.util.function.Function<X, R> fn) {
      return value -> fnRecover(() -> tryApply(value), type, fn);
    }

    /**
     * A function that remember/cache previous executions.
     *
     * @return A memo function.
     */
    default Function<V, R> memoized() {
      if (this instanceof Memoized) {
        return this;
      }
      Map<Object, R> cache = new HashMap<>();
      return (Function<V, R> & Memoized) value -> memo(cache, Arrays.asList(value),
          () -> tryApply(value));
    }
  }

  /**
   * Throwable version of {@link java.util.function.BiFunction}.
   *
   * The {@link #apply(Object, Object)} method throws checked exceptions using {@link #sneakyThrow(Throwable)} method.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   * @param <R> Output type.
   */
  @FunctionalInterface
  public interface Function2<V1, V2, R> extends java.util.function.BiFunction<V1, V2, R> {
    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v1 Input argument.
     * @param v2 Input argument.
     * @return Result.
     * @throws Throwable If something goes wrong.
     */
    R tryApply(V1 v1, V2 v2) throws Throwable;

    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v1 Input argument.
     * @param v2 Input argument.
     * @return Result.
     */
    @Override default R apply(V1 v1, V2 v2) {
      return fn(() -> tryApply(v1, v2));
    }

    /**
     * Apply this function and run the given action in case of exception.
     *
     * @param action Action to run when exception occurs.
     * @return A new function.
     */
    default Function2<V1, V2, R> onFailure(java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     *
     * Apply this function and run the given action in case of exception.
     *
     * @param type Exception filter.
     * @param action Action to run when exception occurs.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function2<V1, V2, R> onFailure(Class<X> type,
        java.util.function.Consumer<X> action) {
      return (v1, v2) -> fnOnFailure(() -> tryApply(v1, v2), type, action);
    }

    /**
     * Apply this function and wrap any resulting exception.
     *
     * @param wrapper Exception wrapper.
     * @return A new function.
     */
    default Function2<V1, V2, R> wrap(java.util.function.Function<Throwable, Exception> wrapper) {
      return (v1, v2) -> fnWrap(() -> tryApply(v1, v2), wrapper);
    }

    /**
     * Apply this function and unwrap any resulting exception. Useful to get clean/shorter stacktrace.
     *
     * @param type Exception to unwrap.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function2<V1, V2, R> unwrap(Class<? extends X> type) {
      return (v1, v2) -> fnUnwrap(() -> tryApply(v1, v2), type);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function2<V1, V2, R> orElse(R defaultValue) {
      return orElse(() -> defaultValue);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function2<V1, V2, R> orElse(Supplier<R> defaultValue) {
      return (v1, v2) -> fn(() -> tryApply(v1, v2), defaultValue);
    }

    /**
     * Apply this function or recover from it in case of exception.
     *
     * @param fn Exception recover.
     * @return A new function.
     */
    default Function2<V1, V2, R> recover(java.util.function.Function<Throwable, R> fn) {
      return recover(Throwable.class, fn);
    }

    /**
     * Apply this function or recover from a specific exception in case of exception.
     *
     * @param type Exception filter.
     * @param fn Exception recover.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function2<V1, V2, R> recover(Class<? extends X> type,
        java.util.function.Function<X, R> fn) {
      return (v1, v2) -> fnRecover(() -> tryApply(v1, v2), type, fn);
    }

    /**
     * A function that remember/cache previous executions.
     *
     * @return A memo function.
     */
    default Function2<V1, V2, R> memoized() {
      if (this instanceof Memoized) {
        return this;
      }
      Map<Object, R> cache = new HashMap<>();
      return (Function2<V1, V2, R> & Memoized) (v1, v2) -> memo(cache, Arrays.asList(v1, v2),
          () -> tryApply(v1, v2));
    }
  }

  /**
   * Function with three arguments.
   *
   * The {@link #apply(Object, Object, Object)} method throws checked exceptions using {@link #sneakyThrow(Throwable)} method.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   * @param <V3> Input type.
   * @param <R> Output type.
   */
  @FunctionalInterface
  public interface Function3<V1, V2, V3, R> {
    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v1 Input argument.
     * @param v2 Input argument.
     * @param v3 Input argument.
     * @return Result.
     * @throws Throwable If something goes wrong.
     */
    R tryApply(V1 v1, V2 v2, V3 v3) throws Throwable;

    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v1 Input argument.
     * @param v2 Input argument.
     * @param v3 Input argument.
     * @return Result.
     */
    default R apply(V1 v1, V2 v2, V3 v3) {
      return fn(() -> tryApply(v1, v2, v3));
    }

    /**
     * Apply this function and run the given action in case of exception.
     *
     * @param action Action to run when exception occurs.
     * @return A new function.
     */
    default Function3<V1, V2, V3, R> onFailure(java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     *
     * Apply this function and run the given action in case of exception.
     *
     * @param type Exception filter.
     * @param action Action to run when exception occurs.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function3<V1, V2, V3, R> onFailure(Class<X> type,
        java.util.function.Consumer<X> action) {
      return (v1, v2, v3) -> fnOnFailure(() -> tryApply(v1, v2, v3), type, action);
    }

    /**
     * Apply this function and wrap any resulting exception.
     *
     * @param wrapper Exception wrapper.
     * @return A new function.
     */
    default Function3<V1, V2, V3, R> wrap(
        java.util.function.Function<Throwable, Exception> wrapper) {
      return (v1, v2, v3) -> fnWrap(() -> tryApply(v1, v2, v3), wrapper);
    }

    /**
     * Apply this function and unwrap any resulting exception. Useful to get clean/shorter stacktrace.
     *
     * @param type Exception to unwrap.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function3<V1, V2, V3, R> unwrap(Class<? extends X> type) {
      return (v1, v2, v3) -> fnUnwrap(() -> tryApply(v1, v2, v3), type);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function3<V1, V2, V3, R> orElse(R defaultValue) {
      return orElse(() -> defaultValue);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function3<V1, V2, V3, R> orElse(Supplier<R> defaultValue) {
      return (v1, v2, v3) -> fn(() -> tryApply(v1, v2, v3), defaultValue);
    }

    /**
     * Apply this function or recover from it in case of exception.
     *
     * @param fn Exception recover.
     * @return A new function.
     */
    default Function3<V1, V2, V3, R> recover(java.util.function.Function<Throwable, R> fn) {
      return recover(Throwable.class, fn);
    }

    /**
     * Apply this function or recover from a specific exception in case of exception.
     *
     * @param type Exception filter.
     * @param fn Exception recover.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function3<V1, V2, V3, R> recover(Class<? extends X> type,
        java.util.function.Function<X, R> fn) {
      return (v1, v2, v3) -> fnRecover(() -> tryApply(v1, v2, v3), type, fn);
    }

    /**
     * A function that remember/cache previous executions.
     *
     * @return A memo function.
     */
    default Function3<V1, V2, V3, R> memoized() {
      if (this instanceof Memoized) {
        return this;
      }
      Map<Object, R> cache = new HashMap<>();
      return (Function3<V1, V2, V3, R> & Memoized) (v1, v2, v3) -> memo(cache,
          Arrays.asList(v1, v2, v3),
          () -> tryApply(v1, v2, v3));
    }
  }

  /**
   * Function with four arguments.
   *
   * The {@link #apply(Object, Object, Object, Object)} method throws checked exceptions using {@link #sneakyThrow(Throwable)} method.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   * @param <V3> Input type.
   * @param <V4> Input type.
   * @param <R> Output type.
   */
  @FunctionalInterface
  public interface Function4<V1, V2, V3, V4, R> {
    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v1 Input argument.
     * @param v2 Input argument.
     * @param v3 Input argument.
     * @param v4 Input argument.
     * @return Result.
     * @throws Throwable If something goes wrong.
     */
    R tryApply(V1 v1, V2 v2, V3 v3, V4 v4) throws Throwable;

    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v1 Input argument.
     * @param v2 Input argument.
     * @param v3 Input argument.
     * @param v4 Input argument.
     * @return Result.
     */
    default R apply(V1 v1, V2 v2, V3 v3, V4 v4) {
      return fn(() -> tryApply(v1, v2, v3, v4));
    }

    /**
     * Apply this function and run the given action in case of exception.
     *
     * @param action Action to run when exception occurs.
     * @return A new function.
     */
    default Function4<V1, V2, V3, V4, R> onFailure(java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     *
     * Apply this function and run the given action in case of exception.
     *
     * @param type Exception filter.
     * @param action Action to run when exception occurs.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function4<V1, V2, V3, V4, R> onFailure(Class<X> type,
        java.util.function.Consumer<X> action) {
      return (v1, v2, v3, v4) -> fnOnFailure(() -> tryApply(v1, v2, v3, v4), type, action);
    }

    /**
     * Apply this function and wrap any resulting exception.
     *
     * @param wrapper Exception wrapper.
     * @return A new function.
     */
    default Function4<V1, V2, V3, V4, R> wrap(
        java.util.function.Function<Throwable, Exception> wrapper) {
      return (v1, v2, v3, v4) -> fnWrap(() -> tryApply(v1, v2, v3, v4), wrapper);
    }

    /**
     * Apply this function and unwrap any resulting exception. Useful to get clean/shorter stacktrace.
     *
     * @param type Exception to unwrap.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function4<V1, V2, V3, V4, R> unwrap(Class<? extends X> type) {
      return (v1, v2, v3, v4) -> fnUnwrap(() -> tryApply(v1, v2, v3, v4), type);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function4<V1, V2, V3, V4, R> orElse(R defaultValue) {
      return orElse(() -> defaultValue);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function4<V1, V2, V3, V4, R> orElse(Supplier<R> defaultValue) {
      return (v1, v2, v3, v4) -> fn(() -> tryApply(v1, v2, v3, v4), defaultValue);
    }

    /**
     * Apply this function or recover from it in case of exception.
     *
     * @param fn Exception recover.
     * @return A new function.
     */
    default Function4<V1, V2, V3, V4, R> recover(java.util.function.Function<Throwable, R> fn) {
      return recover(Throwable.class, fn);
    }

    /**
     * Apply this function or recover from a specific exception in case of exception.
     *
     * @param type Exception filter.
     * @param fn Exception recover.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function4<V1, V2, V3, V4, R> recover(Class<? extends X> type,
        java.util.function.Function<X, R> fn) {
      return (v1, v2, v3, v4) -> fnRecover(() -> tryApply(v1, v2, v3, v4), type, fn);
    }

    /**
     * A function that remember/cache previous executions.
     *
     * @return A memo function.
     */
    default Function4<V1, V2, V3, V4, R> memoized() {
      if (this instanceof Memoized) {
        return this;
      }
      Map<Object, R> cache = new HashMap<>();
      return (Function4<V1, V2, V3, V4, R> & Memoized) (v1, v2, v3, v4) -> memo(cache,
          Arrays.asList(v1, v2, v3, v4),
          () -> tryApply(v1, v2, v3, v4));
    }
  }

  /**
   * Function with five arguments.
   *
   * The {@link #apply(Object, Object, Object, Object, Object)} method throws checked exceptions using {@link #sneakyThrow(Throwable)} method.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   * @param <V3> Input type.
   * @param <V4> Input type.
   * @param <V5> Input type.
   * @param <R> Output type.
   */
  @FunctionalInterface
  public interface Function5<V1, V2, V3, V4, V5, R> {
    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v1 Input argument.
     * @param v2 Input argument.
     * @param v3 Input argument.
     * @param v4 Input argument.
     * @param v5 Input argument.
     * @return Result.
     * @throws Throwable If something goes wrong.
     */
    R tryApply(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5) throws Throwable;

    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v1 Input argument.
     * @param v2 Input argument.
     * @param v3 Input argument.
     * @param v4 Input argument.
     * @param v5 Input argument.
     * @return Result.
     */
    default R apply(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5) {
      return fn(() -> tryApply(v1, v2, v3, v4, v5));
    }

    /**
     * Apply this function and run the given action in case of exception.
     *
     * @param action Action to run when exception occurs.
     * @return A new function.
     */
    default Function5<V1, V2, V3, V4, V5, R> onFailure(
        java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     *
     * Apply this function and run the given action in case of exception.
     *
     * @param type Exception filter.
     * @param action Action to run when exception occurs.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function5<V1, V2, V3, V4, V5, R> onFailure(Class<X> type,
        java.util.function.Consumer<X> action) {
      return (v1, v2, v3, v4, v5) -> fnOnFailure(() -> tryApply(v1, v2, v3, v4, v5), type, action);
    }

    /**
     * Apply this function and wrap any resulting exception.
     *
     * @param wrapper Exception wrapper.
     * @return A new function.
     */
    default Function5<V1, V2, V3, V4, V5, R> wrap(
        java.util.function.Function<Throwable, Exception> wrapper) {
      return (v1, v2, v3, v4, v5) -> fnWrap(() -> tryApply(v1, v2, v3, v4, v5), wrapper);
    }

    /**
     * Apply this function and unwrap any resulting exception. Useful to get clean/shorter stacktrace.
     *
     * @param type Exception to unwrap.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function5<V1, V2, V3, V4, V5, R> unwrap(Class<? extends X> type) {
      return (v1, v2, v3, v4, v5) -> fnUnwrap(() -> tryApply(v1, v2, v3, v4, v5), type);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function5<V1, V2, V3, V4, V5, R> orElse(R defaultValue) {
      return orElse(() -> defaultValue);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function5<V1, V2, V3, V4, V5, R> orElse(Supplier<R> defaultValue) {
      return (v1, v2, v3, v4, v5) -> fn(() -> tryApply(v1, v2, v3, v4, v5), defaultValue);
    }

    /**
     * Apply this function or recover from it in case of exception.
     *
     * @param fn Exception recover.
     * @return A new function.
     */
    default Function5<V1, V2, V3, V4, V5, R> recover(java.util.function.Function<Throwable, R> fn) {
      return recover(Throwable.class, fn);
    }

    /**
     * Apply this function or recover from a specific exception in case of exception.
     *
     * @param type Exception filter.
     * @param fn Exception recover.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function5<V1, V2, V3, V4, V5, R> recover(Class<? extends X> type,
        java.util.function.Function<X, R> fn) {
      return (v1, v2, v3, v4, v5) -> fnRecover(() -> tryApply(v1, v2, v3, v4, v5), type, fn);
    }

    /**
     * A function that remember/cache previous executions.
     *
     * @return A memo function.
     */
    default Function5<V1, V2, V3, V4, V5, R> memoized() {
      if (this instanceof Memoized) {
        return this;
      }
      Map<Object, R> cache = new HashMap<>();
      return (Function5<V1, V2, V3, V4, V5, R> & Memoized) (v1, v2, v3, v4, v5) -> memo(cache,
          Arrays.asList(v1, v2, v3, v4, v5),
          () -> tryApply(v1, v2, v3, v4, v5));
    }
  }

  /**
   * Function with six arguments.
   *
   * The {@link #apply(Object, Object, Object, Object, Object, Object)} method throws checked exceptions using {@link #sneakyThrow(Throwable)} method.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   * @param <V3> Input type.
   * @param <V4> Input type.
   * @param <V5> Input type.
   * @param <V6> Input type.
   * @param <R> Output type.
   */
  @FunctionalInterface
  public interface Function6<V1, V2, V3, V4, V5, V6, R> {
    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v1 Input argument.
     * @param v2 Input argument.
     * @param v3 Input argument.
     * @param v4 Input argument.
     * @param v5 Input argument.
     * @param v6 Input argument.
     * @return Result.
     * @throws Throwable If something goes wrong.
     */
    R tryApply(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5, V6 v6) throws Throwable;

    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v1 Input argument.
     * @param v2 Input argument.
     * @param v3 Input argument.
     * @param v4 Input argument.
     * @param v5 Input argument.
     * @param v6 Input argument.
     * @return Result.
     */
    default R apply(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5, V6 v6) {
      return fn(() -> tryApply(v1, v2, v3, v4, v5, v6));
    }

    /**
     * Apply this function and run the given action in case of exception.
     *
     * @param action Action to run when exception occurs.
     * @return A new function.
     */
    default Function6<V1, V2, V3, V4, V5, V6, R> onFailure(
        java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     *
     * Apply this function and run the given action in case of exception.
     *
     * @param type Exception filter.
     * @param action Action to run when exception occurs.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function6<V1, V2, V3, V4, V5, V6, R> onFailure(Class<X> type,
        java.util.function.Consumer<X> action) {
      return (v1, v2, v3, v4, v5, v6) -> fnOnFailure(() -> tryApply(v1, v2, v3, v4, v5, v6), type,
          action);
    }

    /**
     * Apply this function and wrap any resulting exception.
     *
     * @param wrapper Exception wrapper.
     * @return A new function.
     */
    default Function6<V1, V2, V3, V4, V5, V6, R> wrap(
        java.util.function.Function<Throwable, Exception> wrapper) {
      return (v1, v2, v3, v4, v5, v6) -> fnWrap(() -> tryApply(v1, v2, v3, v4, v5, v6), wrapper);
    }

    /**
     * Apply this function and unwrap any resulting exception. Useful to get clean/shorter stacktrace.
     *
     * @param type Exception to unwrap.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function6<V1, V2, V3, V4, V5, V6, R> unwrap(
        Class<? extends X> type) {
      return (v1, v2, v3, v4, v5, v6) -> fnUnwrap(() -> tryApply(v1, v2, v3, v4, v5, v6), type);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function6<V1, V2, V3, V4, V5, V6, R> orElse(R defaultValue) {
      return orElse(() -> defaultValue);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function6<V1, V2, V3, V4, V5, V6, R> orElse(Supplier<R> defaultValue) {
      return (v1, v2, v3, v4, v5, v6) -> fn(() -> tryApply(v1, v2, v3, v4, v5, v6), defaultValue);
    }

    /**
     * Apply this function or recover from it in case of exception.
     *
     * @param fn Exception recover.
     * @return A new function.
     */
    default Function6<V1, V2, V3, V4, V5, V6, R> recover(
        java.util.function.Function<Throwable, R> fn) {
      return recover(Throwable.class, fn);
    }

    /**
     * Apply this function or recover from a specific exception in case of exception.
     *
     * @param type Exception filter.
     * @param fn Exception recover.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function6<V1, V2, V3, V4, V5, V6, R> recover(
        Class<? extends X> type,
        java.util.function.Function<X, R> fn) {
      return (v1, v2, v3, v4, v5, v6) -> fnRecover(() -> tryApply(v1, v2, v3, v4, v5, v6), type,
          fn);
    }

    /**
     * A function that remember/cache previous executions.
     *
     * @return A memo function.
     */
    default Function6<V1, V2, V3, V4, V5, V6, R> memoized() {
      if (this instanceof Memoized) {
        return this;
      }
      Map<Object, R> cache = new HashMap<>();
      return (Function6<V1, V2, V3, V4, V5, V6, R> & Memoized) (v1, v2, v3, v4, v5, v6) -> memo(
          cache,
          Arrays.asList(v1, v2, v3, v4, v5, v6),
          () -> tryApply(v1, v2, v3, v4, v5, v6));
    }
  }

  /**
   * Function with seven arguments.
   *
   * The {@link #apply(Object, Object, Object, Object, Object, Object, Object)} method throws checked exceptions using {@link #sneakyThrow(Throwable)} method.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   * @param <V3> Input type.
   * @param <V4> Input type.
   * @param <V5> Input type.
   * @param <V6> Input type.
   * @param <V7> Input type.
   * @param <R> Output type.
   */
  @FunctionalInterface
  public interface Function7<V1, V2, V3, V4, V5, V6, V7, R> {
    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v1 Input argument.
     * @param v2 Input argument.
     * @param v3 Input argument.
     * @param v4 Input argument.
     * @param v5 Input argument.
     * @param v6 Input argument.
     * @param v7 Input argument.
     * @return Result.
     * @throws Throwable If something goes wrong.
     */
    R tryApply(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5, V6 v6, V7 v7) throws Throwable;

    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v1 Input argument.
     * @param v2 Input argument.
     * @param v3 Input argument.
     * @param v4 Input argument.
     * @param v5 Input argument.
     * @param v6 Input argument.
     * @param v7 Input argument.
     * @return Result.
     */
    default R apply(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5, V6 v6, V7 v7) {
      return fn(() -> tryApply(v1, v2, v3, v4, v5, v6, v7));
    }

    /**
     * Apply this function and run the given action in case of exception.
     *
     * @param action Action to run when exception occurs.
     * @return A new function.
     */
    default Function7<V1, V2, V3, V4, V5, V6, V7, R> onFailure(
        java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     *
     * Apply this function and run the given action in case of exception.
     *
     * @param type Exception filter.
     * @param action Action to run when exception occurs.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function7<V1, V2, V3, V4, V5, V6, V7, R> onFailure(Class<X> type,
        java.util.function.Consumer<X> action) {
      return (v1, v2, v3, v4, v5, v6, v7) -> fnOnFailure(() -> tryApply(v1, v2, v3, v4, v5, v6, v7),
          type, action);
    }

    /**
     * Apply this function and wrap any resulting exception.
     *
     * @param wrapper Exception wrapper.
     * @return A new function.
     */
    default Function7<V1, V2, V3, V4, V5, V6, V7, R> wrap(
        java.util.function.Function<Throwable, Exception> wrapper) {
      return (v1, v2, v3, v4, v5, v6, v7) -> fnWrap(() -> tryApply(v1, v2, v3, v4, v5, v6, v7),
          wrapper);
    }

    /**
     * Apply this function and unwrap any resulting exception. Useful to get clean/shorter stacktrace.
     *
     * @param type Exception to unwrap.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function7<V1, V2, V3, V4, V5, V6, V7, R> unwrap(
        Class<? extends X> type) {
      return (v1, v2, v3, v4, v5, v6, v7) -> fnUnwrap(() -> tryApply(v1, v2, v3, v4, v5, v6, v7),
          type);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function7<V1, V2, V3, V4, V5, V6, V7, R> orElse(R defaultValue) {
      return orElse(() -> defaultValue);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function7<V1, V2, V3, V4, V5, V6, V7, R> orElse(Supplier<R> defaultValue) {
      return (v1, v2, v3, v4, v5, v6, v7) -> fn(() -> tryApply(v1, v2, v3, v4, v5, v6, v7),
          defaultValue);
    }

    /**
     * Apply this function or recover from it in case of exception.
     *
     * @param fn Exception recover.
     * @return A new function.
     */
    default Function7<V1, V2, V3, V4, V5, V6, V7, R> recover(
        java.util.function.Function<Throwable, R> fn) {
      return recover(Throwable.class, fn);
    }

    /**
     * Apply this function or recover from a specific exception in case of exception.
     *
     * @param type Exception filter.
     * @param fn Exception recover.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function7<V1, V2, V3, V4, V5, V6, V7, R> recover(
        Class<? extends X> type,
        java.util.function.Function<X, R> fn) {
      return (v1, v2, v3, v4, v5, v6, v7) -> fnRecover(() -> tryApply(v1, v2, v3, v4, v5, v6, v7),
          type, fn);
    }

    /**
     * A function that remember/cache previous executions.
     *
     * @return A memo function.
     */
    default Function7<V1, V2, V3, V4, V5, V6, V7, R> memoized() {
      if (this instanceof Memoized) {
        return this;
      }
      Map<Object, R> cache = new HashMap<>();
      return (Function7<V1, V2, V3, V4, V5, V6, V7, R> & Memoized) (v1, v2, v3, v4, v5, v6, v7) -> memo(
          cache,
          Arrays.asList(v1, v2, v3, v4, v5, v6, v7),
          () -> tryApply(v1, v2, v3, v4, v5, v6, v7));
    }
  }

  /**
   * Function with seven arguments.
   *
   * The {@link #apply(Object, Object, Object, Object, Object, Object, Object, Object)} method throws checked exceptions using {@link #sneakyThrow(Throwable)} method.
   *
   * @param <V1> Input type.
   * @param <V2> Input type.
   * @param <V3> Input type.
   * @param <V4> Input type.
   * @param <V5> Input type.
   * @param <V6> Input type.
   * @param <V7> Input type.
   * @param <V8> Input type.
   * @param <R> Output type.
   */
  @FunctionalInterface
  public interface Function8<V1, V2, V3, V4, V5, V6, V7, V8, R> {
    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v1 Input argument.
     * @param v2 Input argument.
     * @param v3 Input argument.
     * @param v4 Input argument.
     * @param v5 Input argument.
     * @param v6 Input argument.
     * @param v7 Input argument.
     * @param v8 Input argument.
     * @return Result.
     * @throws Throwable If something goes wrong.
     */
    R tryApply(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5, V6 v6, V7 v7, V8 v8) throws Throwable;

    /**
     * Apply this function to the given argument and produces a result.
     *
     * @param v1 Input argument.
     * @param v2 Input argument.
     * @param v3 Input argument.
     * @param v4 Input argument.
     * @param v5 Input argument.
     * @param v6 Input argument.
     * @param v7 Input argument.
     * @param v8 Input argument.
     * @return Result.
     */
    default R apply(V1 v1, V2 v2, V3 v3, V4 v4, V5 v5, V6 v6, V7 v7, V8 v8) {
      return fn(() -> tryApply(v1, v2, v3, v4, v5, v6, v7, v8));
    }

    /**
     * Apply this function and run the given action in case of exception.
     *
     * @param action Action to run when exception occurs.
     * @return A new function.
     */
    default Function8<V1, V2, V3, V4, V5, V6, V7, V8, R> onFailure(
        java.util.function.Consumer<Throwable> action) {
      return onFailure(Throwable.class, action);
    }

    /**
     *
     * Apply this function and run the given action in case of exception.
     *
     * @param type Exception filter.
     * @param action Action to run when exception occurs.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function8<V1, V2, V3, V4, V5, V6, V7, V8, R> onFailure(
        Class<X> type,
        java.util.function.Consumer<X> action) {
      return (v1, v2, v3, v4, v5, v6, v7, v8) -> fnOnFailure(
          () -> tryApply(v1, v2, v3, v4, v5, v6, v7, v8),
          type, action);
    }

    /**
     * Apply this function and wrap any resulting exception.
     *
     * @param wrapper Exception wrapper.
     * @return A new function.
     */
    default Function8<V1, V2, V3, V4, V5, V6, V7, V8, R> wrap(
        java.util.function.Function<Throwable, Exception> wrapper) {
      return (v1, v2, v3, v4, v5, v6, v7, v8) -> fnWrap(
          () -> tryApply(v1, v2, v3, v4, v5, v6, v7, v8),
          wrapper);
    }

    /**
     * Apply this function and unwrap any resulting exception. Useful to get clean/shorter stacktrace.
     *
     * @param type Exception to unwrap.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function8<V1, V2, V3, V4, V5, V6, V7, V8, R> unwrap(
        Class<? extends X> type) {
      return (v1, v2, v3, v4, v5, v6, v7, v8) -> fnUnwrap(
          () -> tryApply(v1, v2, v3, v4, v5, v6, v7, v8),
          type);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function8<V1, V2, V3, V4, V5, V6, V7, V8, R> orElse(R defaultValue) {
      return orElse(() -> defaultValue);
    }

    /**
     * Apply this function and returns the given default value in case of exception.
     *
     * @param defaultValue Exceptional default value.
     * @return A new function.
     */
    default Function8<V1, V2, V3, V4, V5, V6, V7, V8, R> orElse(Supplier<R> defaultValue) {
      return (v1, v2, v3, v4, v5, v6, v7, v8) -> fn(() -> tryApply(v1, v2, v3, v4, v5, v6, v7, v8),
          defaultValue);
    }

    /**
     * Apply this function or recover from it in case of exception.
     *
     * @param fn Exception recover.
     * @return A new function.
     */
    default Function8<V1, V2, V3, V4, V5, V6, V7, V8, R> recover(
        java.util.function.Function<Throwable, R> fn) {
      return recover(Throwable.class, fn);
    }

    /**
     * Apply this function or recover from a specific exception in case of exception.
     *
     * @param type Exception filter.
     * @param fn Exception recover.
     * @param <X> Exception type.
     * @return A new function.
     */
    default <X extends Throwable> Function8<V1, V2, V3, V4, V5, V6, V7, V8, R> recover(
        Class<? extends X> type, java.util.function.Function<X, R> fn) {
      return (v1, v2, v3, v4, v5, v6, v7, v8) -> fnRecover(
          () -> tryApply(v1, v2, v3, v4, v5, v6, v7, v8), type, fn);
    }

    /**
     * A function that remember/cache previous executions.
     *
     * @return A memo function.
     */
    default Function8<V1, V2, V3, V4, V5, V6, V7, V8, R> memoized() {
      if (this instanceof Memoized) {
        return this;
      }
      Map<Object, R> cache = new HashMap<>();
      return (Function8<V1, V2, V3, V4, V5, V6, V7, V8, R> & Memoized) (v1, v2, v3, v4, v5, v6, v7, v8) -> memo(
          cache,
          Arrays.asList(v1, v2, v3, v4, v5, v6, v7, v8),
          () -> tryApply(v1, v2, v3, v4, v5, v6, v7, v8));
    }
  }

  public final static <V> Predicate<V> throwingPredicate(Predicate<V> predicate) {
    return predicate;
  }

  public final static <V1, V2> Predicate2<V1, V2> throwingPredicate(Predicate2<V1, V2> predicate) {
    return predicate;
  }

  /**
   * Factory method for {@link Runnable}.
   *
   * @param action Runnable.
   * @return Same runnable.
   */
  public final static Runnable throwingRunnable(Runnable action) {
    return action;
  }

  /**
   * Factory method for {@link Supplier}.
   *
   * @param fn Supplier.
   * @param <V> Resulting value.
   * @return Same supplier.
   */
  public final static <V> Supplier<V> throwingSupplier(Supplier<V> fn) {
    return fn;
  }

  /**
   * Factory method for {@link Function} and {@link java.util.function.Function}.
   *
   * @param fn Function.
   * @param <V> Input value.
   * @param <R> Result value.
   * @return Same supplier.
   */
  public final static <V, R> Function<V, R> throwingFunction(Function<V, R> fn) {
    return fn;
  }

  /**
   * Factory method for {@link Function2} and {@link java.util.function.BiFunction}.
   *
   * @param fn Function.
   * @param <V1> Input value.
   * @param <V2> Input value.
   * @param <R> Result value.
   * @return Same supplier.
   */
  public final static <V1, V2, R> Function2<V1, V2, R> throwingFunction(Function2<V1, V2, R> fn) {
    return fn;
  }

  public final static <V1, V2, V3, R> Function3<V1, V2, V3, R> throwingFunction(
      Function3<V1, V2, V3, R> fn) {
    return fn;
  }

  public final static <V1, V2, V3, V4, R> Function4<V1, V2, V3, V4, R> throwingFunction(
      Function4<V1, V2, V3, V4, R> fn) {
    return fn;
  }

  public final static <V1, V2, V3, V4, V5, R> Function5<V1, V2, V3, V4, V5, R> throwingFunction(
      Function5<V1, V2, V3, V4, V5, R> fn) {
    return fn;
  }

  public final static <V1, V2, V3, V4, V5, V6, R> Function6<V1, V2, V3, V4, V5, V6, R> throwingFunction(
      Function6<V1, V2, V3, V4, V5, V6, R> fn) {
    return fn;
  }

  public final static <V1, V2, V3, V4, V5, V6, V7, R> Function7<V1, V2, V3, V4, V5, V6, V7, R> throwingFunction(
      Function7<V1, V2, V3, V4, V5, V6, V7, R> fn) {
    return fn;
  }

  public final static <V1, V2, V3, V4, V5, V6, V7, V8, R> Function8<V1, V2, V3, V4, V5, V6, V7, V8, R> throwingFunction(
      Function8<V1, V2, V3, V4, V5, V6, V7, V8, R> fn) {
    return fn;
  }

  public final static <V> Consumer<V> throwingConsumer(Consumer<V> action) {
    return action;
  }

  public final static <V1, V2> Consumer2<V1, V2> throwingConsumer(Consumer2<V1, V2> action) {
    return action;
  }

  public final static <V1, V2, V3> Consumer3<V1, V2, V3> throwingConsumer(
      Consumer3<V1, V2, V3> action) {
    return action;
  }

  public final static <V1, V2, V3, V4> Consumer4<V1, V2, V3, V4> throwingConsumer(
      Consumer4<V1, V2, V3, V4> action) {
    return action;
  }

  public final static <V1, V2, V3, V4, V5> Consumer5<V1, V2, V3, V4, V5> throwingConsumer(
      Consumer5<V1, V2, V3, V4, V5> action) {
    return action;
  }

  public final static <V1, V2, V3, V4, V5, V6> Consumer6<V1, V2, V3, V4, V5, V6> throwingConsumer(
      Consumer6<V1, V2, V3, V4, V5, V6> action) {
    return action;
  }

  public final static <V1, V2, V3, V4, V5, V6, V7> Consumer7<V1, V2, V3, V4, V5, V6, V7> throwingConsumer(
      Consumer7<V1, V2, V3, V4, V5, V6, V7> action) {
    return action;
  }

  public final static <V1, V2, V3, V4, V5, V6, V7, V8> Consumer8<V1, V2, V3, V4, V5, V6, V7, V8> throwingConsumer(
      Consumer8<V1, V2, V3, V4, V5, V6, V7, V8> action) {
    return action;
  }

  /**
   * Throws any throwable 'sneakily' - you don't need to catch it, nor declare that you throw it
   * onwards.
   * The exception is still thrown - javac will just stop whining about it.
   * <p>
   * Example usage:
   * <pre>public void run() {
   *     throw sneakyThrow(new IOException("You don't need to catch me!"));
   * }</pre>
   * <p>
   * NB: The exception is not wrapped, ignored, swallowed, or redefined. The JVM actually does not
   * know or care
   * about the concept of a 'checked exception'. All this method does is hide the act of throwing a
   * checked exception from the java compiler.
   * <p>
   * Note that this method has a return type of {@code RuntimeException}; it is advised you always
   * call this
   * method as argument to the {@code throw} statement to avoid compiler errors regarding no return
   * statement and similar problems. This method won't of course return an actual
   * {@code RuntimeException} -
   * it never returns, it always throws the provided exception.
   *
   * @param x The throwable to throw without requiring you to catch its type.
   * @return A dummy RuntimeException; this method never returns normally, it <em>always</em> throws
   *         an exception!
   */
  public static RuntimeException sneakyThrow(final Throwable x) {
    if (x == null) {
      throw new NullPointerException("x");
    }

    sneakyThrow0(x);
    return null;
  }

  /**
   * True if the given exception is one of {@link InterruptedException}, {@link LinkageError},
   * {@link ThreadDeath}, {@link VirtualMachineError}.
   *
   * @param x Exception to test.
   * @return True if the given exception is one of {@link InterruptedException}, {@link LinkageError},
   *     {@link ThreadDeath}, {@link VirtualMachineError}.
   */
  public static boolean isFatal(Throwable x) {
    return x instanceof InterruptedException ||
        x instanceof LinkageError ||
        x instanceof ThreadDeath ||
        x instanceof VirtualMachineError;
  }

  /**
   * Make a checked exception un-checked and rethrow it.
   *
   * @param x Exception to throw.
   * @param <E> Exception type.
   * @throws E Exception to throw.
   */
  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneakyThrow0(final Throwable x) throws E {
    throw (E) x;
  }

  private static void runAction(Runnable action) {
    try {
      action.tryRun();
    } catch (Throwable x) {
      throw sneakyThrow(x);
    }
  }

  private static <V> V fn(Supplier<V> fn) {
    try {
      return fn.tryGet();
    } catch (Throwable x) {
      throw sneakyThrow(x);
    }
  }

  private static <R> R fn(Supplier<R> fn, Supplier<R> orElse) {
    try {
      return fn.tryGet();
    } catch (Throwable x) {
      if (isFatal(x)) {
        throw sneakyThrow(x);
      }
      return orElse.get();
    }
  }

  private static <R, X extends Throwable> R fnRecover(Supplier<R> fn, Class<? extends X> type,
      java.util.function.Function<X, R> recover) {
    try {
      return fn.tryGet();
    } catch (Throwable x) {
      if (isFatal(x)) {
        throw sneakyThrow(x);
      }
      if (type.isInstance(x)) {
        return recover.apply(type.cast(x));
      }
      throw sneakyThrow(x);
    }
  }

  private static <V, X extends Throwable> V fnOnFailure(Supplier<V> fn, Class<? extends X> type,
      java.util.function.Consumer<X> consumer) {
    try {
      return fn.tryGet();
    } catch (Throwable x) {
      if (type.isInstance(x)) {
        consumer.accept(type.cast(x));
      }
      throw sneakyThrow(x);
    }
  }

  private static <V> V fnWrap(Supplier<V> fn,
      java.util.function.Function<Throwable, Exception> wrapper) {
    try {
      return fn.tryGet();
    } catch (Throwable x) {
      if (isFatal(x)) {
        throw sneakyThrow(x);
      }
      throw sneakyThrow(wrapper.apply(x));
    }
  }

  private static <V, X extends Throwable> V fnUnwrap(Supplier<V> fn, Class<? extends X> type) {
    try {
      return fn.tryGet();
    } catch (Throwable x) {
      if (isFatal(x)) {
        throw sneakyThrow(x);
      }
      Throwable t = x;
      if (type.isInstance(x)) {
        t = Optional.ofNullable(x.getCause()).orElse(x);
      }
      throw sneakyThrow(t);
    }
  }

  private static <X extends Throwable> void runOnFailure(Runnable action, Class<? extends X> type,
      java.util.function.Consumer<X> consumer) {
    try {
      action.tryRun();
    } catch (Throwable x) {
      if (type.isInstance(x)) {
        consumer.accept(type.cast(x));
      }
      throw sneakyThrow(x);
    }
  }

  private static void runWrap(Runnable action,
      java.util.function.Function<Throwable, Exception> wrapper) {
    try {
      action.tryRun();
    } catch (Throwable x) {
      if (isFatal(x)) {
        throw sneakyThrow(x);
      }
      throw sneakyThrow(wrapper.apply(x));
    }
  }

  private static <X extends Throwable> void runUnwrap(Runnable action, Class<? extends X> type) {
    try {
      action.tryRun();
    } catch (Throwable x) {
      if (isFatal(x)) {
        throw sneakyThrow(x);
      }
      Throwable t = x;
      if (type.isInstance(x)) {
        t = Optional.ofNullable(x.getCause()).orElse(x);
      }
      throw sneakyThrow(t);
    }
  }

  private final static <R> R memo(Map<Object, R> cache, List<Object> key, Supplier<R> fn) {
    synchronized (cache) {
      R value = cache.get(key);
      if (value == null) {
        value = fn.get();
        cache.put(key, value);
      }
      return value;
    }
  }
}
