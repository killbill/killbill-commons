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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Functional try and try-with-resources implementation.
 */
public abstract class Try {

  /** Try with a value. */
  public static abstract class Value<V> extends Try {

    /**
     * Gets the success result or {@link Throwing#sneakyThrow(Throwable)} the exception.
     *
     * @return The success result or {@link Throwing#sneakyThrow(Throwable)} the exception.
     */
    public abstract V get();

    /**
     * Get the success value or use the given function on failure.
     *
     * @param value Default value provider.
     * @return Success or default value.
     */
    public V orElseGet(Supplier<V> value) {
      return isSuccess() ? get() : value.get();
    }

    /**
     * Get the success value or use the given default value on failure.
     *
     * @param value Default value.
     * @return Success or default value.
     */
    public V orElse(V value) {
      return isSuccess() ? get() : value;
    }

    /**
     * Get the success value or throw an exception created by the exception provider.
     *
     * @param provider Exception provider.
     * @return Success value.
     */
    public V orElseThrow(Throwing.Function<Throwable, Throwable> provider) {
      if (isSuccess()) {
        return get();
      }
      throw Throwing.sneakyThrow(provider.apply(getCause().get()));
    }

    /**
     * Always run the given action, works like a finally clause.
     *
     * @param action Finally action.
     * @return This try result.
     */
    @Override public Value<V> onComplete(Throwing.Runnable action) {
      return (Value<V>) super.onComplete(action);
    }

    @Override public Value<V> onComplete(final Throwing.Consumer<Throwable> action) {
      return (Value<V>) super.onComplete(action);
    }

    /**
     * Always run the given action, works like a finally clause. Exception and value might be null.
     * Exception will be null in case of success.
     *
     * @param action Finally action.
     * @return This try result.
     */
    public Value<V> onComplete(final Throwing.Consumer2<V, Throwable> action) {
      try {
        V value = isSuccess() ? get() : null;
        action.accept(value, getCause().orElse(null));
        return this;
      } catch (Throwable x) {
        return (Value<V>) failure(x);
      }
    }

    /**
     * Run the given action if and only if this is a failure.
     *
     * @param action Failure action/listener.
     * @return This try.
     */
    @Override public Value<V> onFailure(final Consumer<? super Throwable> action) {
      super.onFailure(action);
      return this;
    }

    /**
     * Run the given action if and only if this is a success.
     *
     * @param action Success listener.
     * @return This try.
     */
    @Override public Value<V> onSuccess(final Runnable action) {
      super.onSuccess(action);
      return this;
    }

    /**
     * Run the given action if and only if this is a success.
     *
     * @param action Success listener.
     * @return This try.
     */
    public Value<V> onSuccess(final Consumer<V> action) {
      if (isSuccess()) {
        action.accept(get());
      }
      return this;
    }

    /**
     * Recover from failure. The recover function will be executed in case of failure.
     *
     * @param fn Recover function.
     * @return This try on success, a new success try from recover or a failure try in case of exception.
     */
    public Value<V> recoverWith(Throwing.Function<Throwable, Value<V>> fn) {
      return recoverWith(Throwable.class, fn);
    }

    /**
     * Recover from failure if and only if the exception is a subclass of the given exception filter.
     * The recover function will be executed in case of failure.
     *
     * @param exception Exception filter.
     * @param fn Recover function.
     * @param <X> Exception type.
     * @return This try on success, a new success try from recover or a failure try in case of exception.
     */
    public <X extends Throwable> Value<V> recoverWith(Class<X> exception,
      Throwing.Function<X, Value<V>> fn) {
      return (Value<V>) getCause()
        .filter(exception::isInstance)
        .map(x -> {
          try {
            return fn.apply((X) x);
          } catch (Throwable ex) {
            return failure(ex);
          }
        })
        .orElse(this);
    }

    /**
     * Recover from failure. The recover function will be executed in case of failure.
     *
     * @param fn Recover function.
     * @return This try on success, a new success try from recover or a failure try in case of exception.
     */
    public Value<V> recover(Throwing.Function<Throwable, V> fn) {
      return recover(Throwable.class, fn);
    }

    /**
     * Recover from failure if and only if the exception is a subclass of the given exception filter.
     * The recover function will be executed in case of failure.
     *
     * @param exception Exception filter.
     * @param value Recover value.
     * @param <X> Exception type.
     * @return This try on success, a new success try from recover or a failure try in case of exception.
     */
    public <X extends Throwable> Value<V> recover(Class<X> exception, V value) {
      return recoverWith(exception, x -> Try.success(value));
    }

    /**
     * Recover from failure if and only if the exception is a subclass of the given exception filter.
     * The recover function will be executed in case of failure.
     *
     * @param exception Exception filter.
     * @param fn Recover function.
     * @param <X> Exception type.
     * @return This try on success, a new success try from recover or a failure try in case of exception.
     */
    public <X extends Throwable> Value<V> recover(Class<X> exception, Throwing.Function<X, V> fn) {
      return recoverWith(exception, x -> Try.apply(() -> fn.apply(x)));
    }

    /**
     * Flat map the success value.
     *
     * @param mapper Mapper.
     * @param <T> New type.
     * @return A new try value for success or failure.
     */
    public <T> Value<T> flatMap(Throwing.Function<V, Value<T>> mapper) {
      if (isFailure()) {
        return (Value<T>) this;
      }
      try {
        return mapper.apply(get());
      } catch (Throwable x) {
        return new Failure<>(x);
      }
    }

    /**
     * Map the success value.
     *
     * @param mapper Mapper.
     * @param <T> New type.
     * @return A new try value for success or failure.
     */
    public <T> Value<T> map(Throwing.Function<V, T> mapper) {
      return flatMap(v -> new Success<>(mapper.apply(v)));
    }

    /**
     * Get an empty optional in case of failure.
     *
     * @return An empty optional in case of failure.
     */
    public Optional<V> toOptional() {
      return isFailure() ? Optional.empty() : Optional.ofNullable(get());
    }

    @Override public <X extends Throwable> Value<V> unwrap(Class<? extends X> type) {
      return (Value<V>) super.unwrap(type);
    }

    @Override public Value<V> unwrap(final Throwing.Predicate<Throwable> predicate) {
      return (Value<V>) super.unwrap(predicate);
    }

    @Override public Value<V> wrap(final Throwing.Function<Throwable, Throwable> wrapper) {
      return (Value<V>) super.wrap(wrapper);
    }

    @Override public <X extends Throwable> Value<V> wrap(final Class<? extends X> predicate,
      final Throwing.Function<X, Throwable> wrapper) {
      return (Value<V>) super.wrap(predicate, wrapper);
    }

    @Override public <X extends Throwable> Value<V> wrap(final Throwing.Predicate<X> predicate,
      final Throwing.Function<X, Throwable> wrapper) {
      return (Value<V>) super.wrap(predicate, wrapper);
    }
  }

  private static class Success<V> extends Value<V> {
    private final V value;

    public Success(V value) {
      this.value = value;
    }

    @Override public V get() {
      return value;
    }

    @Override public Optional<Throwable> getCause() {
      return Optional.empty();
    }
  }

  private static class Failure<V> extends Value<V> {
    private final Throwable x;

    public Failure(Throwable x) {
      this.x = x;
    }

    @Override public V get() {
      throw Throwing.sneakyThrow(x);
    }

    @Override public Optional<Throwable> getCause() {
      return Optional.of(x);
    }
  }

  /**
   * Try with resource implementation.
   *
   * @param <R> Resource type.
   */
  public static class ResourceHandler<R extends AutoCloseable> {

    private static class ProxyCloseable<P extends AutoCloseable, R extends AutoCloseable>
      implements AutoCloseable, Throwing.Supplier<R> {

      private final Throwing.Supplier<P> parent;
      private final Throwing.Function<P, R> mapper;
      private P parentResource;
      private R resource;

      public ProxyCloseable(Throwing.Supplier<P> parent, Throwing.Function<P, R> mapper) {
        this.parent = parent;
        this.mapper = mapper;
      }

      @Override public void close() throws Exception {
        try {
          Optional.ofNullable(resource)
            .ifPresent(Throwing.throwingConsumer(AutoCloseable::close));
        } finally {
          if (parent instanceof ProxyCloseable) {
            ((ProxyCloseable) parent).close();
          } else {
            Optional.ofNullable(parentResource)
              .ifPresent(Throwing.throwingConsumer(AutoCloseable::close));
          }
        }
      }

      @Override public R tryGet() throws Throwable {
        if (parent instanceof ProxyCloseable) {
          ProxyCloseable proxy = (ProxyCloseable) parent;
          if (proxy.resource == null) {
            proxy.get();
          }
          this.parentResource = (P) proxy.resource;
        } else {
          this.parentResource = parent.get();
        }
        this.resource = mapper.apply(parentResource);
        return (R) this;
      }
    }

    private final Throwing.Supplier<R> r;

    private ResourceHandler(Throwing.Supplier<R> r1) {
      this.r = r1;
    }

    /**
     * Map the resource to a new closeable resource.
     *
     * @param fn Mapper.
     * @param <V> New resource type.
     * @return A new resource handler.
     */
    public <V extends AutoCloseable> ResourceHandler<V> map(Throwing.Function<R, V> fn) {
      return new ResourceHandler<>(new ProxyCloseable<>(this.r, fn));
    }

    /**
     * Apply the resource and produces an output.
     *
     * @param fn Function to apply.
     * @param <V> Output type.
     * @return A new try result.
     */
    public <V> Value<V> apply(Throwing.Function<R, V> fn) {
      return Try.apply(() -> {
        try (R r1 = this.r.get()) {
          if (r1 instanceof ProxyCloseable) {
            return fn.apply((R) ((ProxyCloseable) r1).resource);
          }
          return fn.apply(r1);
        }
      });
    }

    /**
     * Run an operation over the resource.
     *
     * @param fn Function to apply.
     * @return A new try result.
     */
    public Try run(Throwing.Consumer<R> fn) {
      return Try.run(() -> {
        try (R r1 = this.r.get()) {
          fn.accept(r1);
        }
      });
    }
  }

  /**
   * Try with resource implementation.
   *
   * @param <R1> Resource type.
   * @param <R2> Resource type.
   */
  public static class ResourceHandler2<R1 extends AutoCloseable, R2 extends AutoCloseable> {
    private final Throwing.Supplier<R1> r1;
    private final Throwing.Supplier<R2> r2;

    private ResourceHandler2(Throwing.Supplier<R1> r1, Throwing.Supplier<R2> r2) {
      this.r1 = r1;
      this.r2 = r2;
    }

    public <V> Value<V> apply(Throwing.Function2<R1, R2, V> fn) {
      return Try.apply(() -> {
        try (R1 r1 = this.r1.get(); R2 r2 = this.r2.get()) {
          return fn.apply(r1, r2);
        }
      });
    }

    public Try run(Throwing.Consumer2<R1, R2> fn) {
      return Try.run(() -> {
        try (R1 r1 = this.r1.get(); R2 r2 = this.r2.get()) {
          fn.accept(r1, r2);
        }
      });
    }
  }

  public static class ResourceHandler3<R1 extends AutoCloseable, R2 extends AutoCloseable, R3 extends AutoCloseable> {
    private final Throwing.Supplier<R1> r1;
    private final Throwing.Supplier<R2> r2;
    private final Throwing.Supplier<R3> r3;

    private ResourceHandler3(Throwing.Supplier<R1> r1, Throwing.Supplier<R2> r2,
      Throwing.Supplier<R3> r3) {
      this.r1 = r1;
      this.r2 = r2;
      this.r3 = r3;
    }

    public <V> Value<V> apply(Throwing.Function3<R1, R2, R3, V> fn) {
      return Try.apply(() -> {
        try (R1 r1 = this.r1.get(); R2 r2 = this.r2.get(); R3 r3 = this.r3.get()) {
          return fn.apply(r1, r2, r3);
        }
      });
    }

    public Try run(Throwing.Consumer3<R1, R2, R3> fn) {
      return Try.run(() -> {
        try (R1 r1 = this.r1.get(); R2 r2 = this.r2.get(); R3 r3 = this.r3.get()) {
          fn.accept(r1, r2, r3);
        }
      });
    }
  }

  public static class ResourceHandler4<R1 extends AutoCloseable, R2 extends AutoCloseable, R3 extends AutoCloseable, R4 extends AutoCloseable> {
    private final Throwing.Supplier<R1> r1;
    private final Throwing.Supplier<R2> r2;
    private final Throwing.Supplier<R3> r3;
    private final Throwing.Supplier<R4> r4;

    private ResourceHandler4(Throwing.Supplier<R1> r1, Throwing.Supplier<R2> r2,
      Throwing.Supplier<R3> r3, Throwing.Supplier<R4> r4) {
      this.r1 = r1;
      this.r2 = r2;
      this.r3 = r3;
      this.r4 = r4;
    }

    public <V> Value<V> apply(Throwing.Function4<R1, R2, R3, R4, V> fn) {
      return Try.apply(() -> {
        try (R1 r1 = this.r1.get();
          R2 r2 = this.r2.get();
          R3 r3 = this.r3.get();
          R4 r4 = this.r4.get()) {
          return fn.apply(r1, r2, r3, r4);
        }
      });
    }

    public Try run(Throwing.Consumer4<R1, R2, R3, R4> fn) {
      return Try.run(() -> {
        try (R1 r1 = this.r1.get();
          R2 r2 = this.r2.get();
          R3 r3 = this.r3.get();
          R4 r4 = this.r4.get()) {
          fn.accept(r1, r2, r3, r4);
        }
      });
    }
  }

  /**
   * Functional try-with-resources:
   *
   * <pre>{@code
   *  InputStream in = ...;
   *
   *  byte[] content = Try.of(in)
   *    .apply(in -> read(in))
   *    .get();
   *
   * }</pre>
   *
   * Jdbc example:
   *
   * <pre>{@code
   *  Connection connection = ...;
   *
   *  Try.of(connection)
   *     .map(c -> c.preparedStatement("..."))
   *     .map(stt -> stt.executeQuery())
   *     .apply(rs-> {
   *       return res.getString("column");
   *     })
   *     .get();
   *
   * }</pre>
   *
   * @param r1 Input resource.
   * @param <R> Resource type.
   * @return A resource handler.
   */
  public final static <R extends AutoCloseable> ResourceHandler<R> of(R r1) {
    return with(() -> r1);
  }

  /**
   * Functional try-with-resources:
   *
   * <pre>{@code
   *  InputStream in = ...;
   *  OutputStream out = ...;
   *
   *  Try.of(in, out)
   *    .run((from, to) -> copy(from, to))
   *    .onFailure(Throwable::printStacktrace);
   *
   * }</pre>
   *
   * @param r1 Input resource.
   * @param r2 Input resource.
   * @param <R1> Resource type.
   * @param <R2> Resource type.
   * @return A resource handler.
   */
  public final static <R1 extends AutoCloseable, R2 extends AutoCloseable> ResourceHandler2<R1, R2> of(
    R1 r1, R2 r2) {
    return with(() -> r1, () -> r2);
  }

  /**
   * Functional try-with-resources with 3 inputs.
   *
   * @param r1 Input resource.
   * @param r2 Input resource.
   * @param r3 Input resource.
   * @param <R1> Resource type.
   * @param <R2> Resource type.
   * @param <R3> Resource type.
   * @return A resource handler.
   */
  public final static <R1 extends AutoCloseable, R2 extends AutoCloseable, R3 extends AutoCloseable> ResourceHandler3<R1, R2, R3> of(
    R1 r1, R2 r2, R3 r3) {
    return with(() -> r1, () -> r2, () -> r3);
  }

  /**
   * Functional try-with-resources with 4 inputs.
   *
   * @param r1 Input resource.
   * @param r2 Input resource.
   * @param r3 Input resource.
   * @param r4 Input resource.
   * @param <R1> Resource type.
   * @param <R2> Resource type.
   * @param <R3> Resource type.
   * @param <R4> Resource type.
   * @return A resource handler.
   */
  public final static <R1 extends AutoCloseable, R2 extends AutoCloseable, R3 extends AutoCloseable, R4 extends AutoCloseable> ResourceHandler4<R1, R2, R3, R4> of(
    R1 r1, R2 r2, R3 r3, R4 r4) {
    return with(() -> r1, () -> r2, () -> r3, () -> r4);
  }

  /**
   * Functional try-with-resources:
   *
   * <pre>{@code
   *  byte[] content = Try.with(() -> newInputStream())
   *    .apply(in -> read(in))
   *    .get();
   *
   * }</pre>
   *
   * Jdbc example:
   *
   * <pre>{@code
   *  Try.with(() -> newConnection())
   *     .map(c -> c.preparedStatement("..."))
   *     .map(stt -> stt.executeQuery())
   *     .apply(rs-> {
   *       return res.getString("column");
   *     })
   *     .get();
   *
   * }</pre>
   *
   * @param r1 Input resource.
   * @param <R> Resource type.
   * @return A resource handler.
   */
  public final static <R extends AutoCloseable> ResourceHandler<R> with(Throwing.Supplier<R> r1) {
    return new ResourceHandler<>(r1);
  }

  /**
   * Functional try-with-resources:
   *
   * <pre>{@code
   *  Try.with(() -> newIn(), () -> newOut())
   *    .run((from, to) -> copy(from, to))
   *    .onFailure(Throwable::printStacktrace);
   * }</pre>
   *
   * @param r1 Input resource.
   * @param r2 Input resource.
   * @param <R1> Resource type.
   * @param <R2> Resource type.
   * @return A resource handler.
   */
  public final static <R1 extends AutoCloseable, R2 extends AutoCloseable> ResourceHandler2<R1, R2> with(
    Throwing.Supplier<R1> r1, Throwing.Supplier<R2> r2) {
    return new ResourceHandler2<>(r1, r2);
  }

  /**
   * Functional try-with-resources with 3 inputs.
   *
   * @param r1 Input resource.
   * @param r2 Input resource.
   * @param r3 Input resource.
   * @param <R1> Resource type.
   * @param <R2> Resource type.
   * @param <R3> Resource type.
   * @return A resource handler.
   */
  public final static <R1 extends AutoCloseable, R2 extends AutoCloseable, R3 extends AutoCloseable> ResourceHandler3<R1, R2, R3> with(
    Throwing.Supplier<R1> r1, Throwing.Supplier<R2> r2, Throwing.Supplier<R3> r3) {
    return new ResourceHandler3<>(r1, r2, r3);
  }

  /**
   * Functional try-with-resources with 4 inputs.
   *
   * @param r1 Input resource.
   * @param r2 Input resource.
   * @param r3 Input resource.
   * @param r4 Input resource.
   * @param <R1> Resource type.
   * @param <R2> Resource type.
   * @param <R3> Resource type.
   * @param <R4> Resource type.
   * @return A resource handler.
   */
  public final static <R1 extends AutoCloseable, R2 extends AutoCloseable, R3 extends AutoCloseable, R4 extends AutoCloseable> ResourceHandler4<R1, R2, R3, R4> with(
    Throwing.Supplier<R1> r1, Throwing.Supplier<R2> r2, Throwing.Supplier<R3> r3,
    Throwing.Supplier<R4> r4) {
    return new ResourceHandler4<>(r1, r2, r3, r4);
  }

  /**
   * Get a new success value.
   *
   * @param value Value.
   * @param <V> Value type.
   * @return A new success value.
   */
  public final static <V> Value<V> success(V value) {
    return new Success<>(value);
  }

  /**
   * Get a new failure value.
   *
   * @param x Exception.
   * @return A new failure value.
   */
  public final static Value<Throwable> failure(Throwable x) {
    return new Failure<>(x);
  }

  /**
   * Creates a new try from given value provider.
   *
   * @param fn Value provider.
   * @param <V> Value type.
   * @return A new success try or failure try in case of exception.
   */
  public static <V> Value<V> apply(Throwing.Supplier<? extends V> fn) {
    try {
      return new Success<>(fn.get());
    } catch (Throwable x) {
      return new Failure(x);
    }
  }

  /**
   * Creates a new try from given callable.
   *
   * @param fn Callable.
   * @param <V> Value type.
   * @return A new success try or failure try in case of exception.
   */
  public static <V> Value<V> call(Callable<? extends V> fn) {
    return apply(fn::call);
  }

  /**
   * Creates a side effect try from given runnable. Don't forget to either throw or log the exception
   * in case of failure. Unless, of course you don't care about the exception.
   *
   * Log the exception:
   * <pre>{@code
   *   Try.run(() -> ...)
   *     .onFailure(x -> x.printStacktrace());
   * }</pre>
   *
   * Throw the exception:
   * <pre>{@code
   *   Try.run(() -> ...)
   *     .throwException();
   * }</pre>
   *
   * @param runnable Runnable.
   * @return A void try.
   */
  public static Try run(Throwing.Runnable runnable) {
    try {
      runnable.run();
      return new Success<>(null);
    } catch (Throwable x) {
      return new Failure(x);
    }
  }

  /**
   * True in case of failure.
   *
   * @return True in case of failure.
   */
  public boolean isFailure() {
    return getCause().isPresent();
  }

  /**
   * True in case of success.
   *
   * @return True in case of success.
   */
  public boolean isSuccess() {
    return !isFailure();
  }

  /**
   * Run the given action if and only if this is a failure.
   *
   * @param action Failure listener.
   * @return This try.
   */
  public Try onFailure(Consumer<? super Throwable> action) {
    getCause().ifPresent(action);
    return this;
  }

  /**
   * Run the given action if and only if this is a success.
   *
   * @param action Success listener.
   * @return This try.
   */
  public Try onSuccess(Runnable action) {
    if (isSuccess()) {
      action.run();
    }
    return this;
  }

  /**
   * In case of failure unwrap the exception provided by calling {@link Throwable#getCause()}.
   * Useful for clean/shorter stackstrace.
   *
   * Example for {@link java.lang.reflect.InvocationTargetException}:
   *
   * <pre>{@code
   * Try.run(() -> {
   *   Method m = ...;
   *   m.invoke(...); //might throw InvocationTargetException
   * }).unwrap(InvocationTargetException.class)
   *   .throwException();
   * }</pre>
   *
   * @param type Exception filter.
   * @param <X> Exception type.
   * @return This try for success or a new failure with exception unwrap.
   */
  public <X extends Throwable> Try unwrap(Class<? extends X> type) {
    return unwrap(type::isInstance);
  }

  /**
   * In case of failure unwrap the exception provided by calling {@link Throwable#getCause()}.
   * Useful for clean/shorter stackstrace.
   *
   * Example for {@link java.lang.reflect.InvocationTargetException}:
   *
   * <pre>{@code
   * Try.run(() -> {
   *   Method m = ...;
   *   m.invoke(...); //might throw InvocationTargetException
   * }).unwrap(InvocationTargetException.class::isInstance)
   *   .throwException();
   * }</pre>
   *
   * @param predicate Exception filter.
   * @return This try for success or a new failure with exception unwrap.
   */
  public Try unwrap(Throwing.Predicate<Throwable> predicate) {
    try {
      return getCause()
        .filter(predicate)
        .map(Throwable::getCause)
        .filter(Objects::nonNull)
        .map(x -> (Try) Try.failure(x))
        .orElse(this);
    } catch (Throwable x) {
      return failure(x);
    }
  }

  /**
   * In case of failure wrap an exception matching the given predicate to something else.
   *
   * @param wrapper Exception mapper.
   * @return This try for success or a new failure with exception wrapped.
   */
  public Try wrap(Throwing.Function<Throwable, Throwable> wrapper) {
    return wrap(Throwable.class, wrapper);
  }

  /**
   * In case of failure wrap an exception matching the given predicate to something else.
   *
   * @param predicate Exception predicate.
   * @param wrapper Exception mapper.
   * @param <X> Exception type.
   * @return This try for success or a new failure with exception wrapped.
   */
  public <X extends Throwable> Try wrap(Class<? extends X> predicate,
    Throwing.Function<X, Throwable> wrapper) {
    return wrap(predicate::isInstance, wrapper);
  }

  /**
   * In case of failure wrap an exception matching the given predicate to something else.
   *
   * @param predicate Exception predicate.
   * @param wrapper Exception mapper.
   * @param <X> Exception type.
   * @return This try for success or a new failure with exception wrapped.
   */
  public <X extends Throwable> Try wrap(Throwing.Predicate<X> predicate,
    Throwing.Function<X, Throwable> wrapper) {
    try {
      return getCause()
        .filter(x -> predicate.test((X) x))
        .map(x -> (Try) Try.failure(wrapper.apply((X) x)))
        .orElse(this);
    } catch (Throwable x) {
      return failure(x);
    }
  }

  /**
   * Always run the given action, works like a finally clause.
   *
   * @param action Finally action.
   * @return This try result.
   */
  public Try onComplete(Throwing.Runnable action) {
    try {
      action.run();
      return this;
    } catch (Throwable x) {
      return Try.failure(x);
    }
  }

  /**
   * Always run the given action, works like a finally clause. Exception will be null in case of success.
   *
   * @param action Finally action.
   * @return This try result.
   */
  public Try onComplete(Throwing.Consumer<Throwable> action) {
    try {
      action.accept(getCause().orElse(null));
      return this;
    } catch (Throwable x) {
      return Try.failure(x);
    }
  }

  /**
   * Propagate/throw the exception in case of failure.
   */
  public void throwException() {
    getCause().ifPresent(Throwing::sneakyThrow);
  }

  /**
   * Cause for failure or empty optional for success result.
   *
   * @return Cause for failure or empty optional for success result.
   */
  public abstract Optional<Throwable> getCause();

}
