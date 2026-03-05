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
package org.jooby;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import static java.util.Objects.requireNonNull;
import org.jooby.funzy.Throwing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Allows to optimize, customize or apply defaults values for application services.
 *
 * <p>
 * A env is represented by it's name. For example: <code>dev</code>, <code>prod</code>, etc... A
 * <strong>dev</strong> env is special and a module provider could do some special configuration for
 * development, like turning off a cache, reloading of resources, etc.
 * </p>
 * <p>
 * Same is true for not <strong>dev</strong> environments. For example, a module provider might
 * create a high performance connection pool, caches, etc.
 * </p>
 * <p>
 * By default env is set to <code>dev</code>, but you can change it by setting the
 * <code>application.env</code> property to anything else.
 * </p>
 *
 * @author edgar
 * @since 0.1.0
 */
public interface Env extends LifeCycle {

  /**
   * Property source for {@link Resolver}
   *
   * @author edgar
   * @since 1.1.0
   */
  interface PropertySource {

    /**
     * Get a property value or throw {@link NoSuchElementException}.
     *
     * @param key Property key/name.
     * @return Value or throw {@link NoSuchElementException}.
     * @throws NoSuchElementException If property is missing.
     */
    @Nonnull
    String get(String key) throws NoSuchElementException;
  }

  /**
   * {@link PropertySource} for {@link Config}.
   *
   * @author edgar
   * @since 1.1.0
   */
  class ConfigSource implements PropertySource {

    private Config source;

    public ConfigSource(final Config source) {
      this.source = source;
    }

    @Override
    public String get(final String key) throws NoSuchElementException {
      if (source.hasPath(key)) {
        return source.getString(key);
      }
      throw new NoSuchElementException(key);
    }

  }

  /**
   * {@link PropertySource} for {@link Map}.
   *
   * @author edgar
   * @since 1.1.0
   */
  class MapSource implements PropertySource {

    private Map<String, Object> source;

    public MapSource(final Map<String, Object> source) {
      this.source = source;
    }

    @Override
    public String get(final String key) throws NoSuchElementException {
      Object value = source.get(key);
      if (value != null) {
        return value.toString();
      }
      throw new NoSuchElementException(key);
    }

  }

  /**
   * Template literal implementation, replaces <code>${expression}</code> from a String using a
   * {@link Config} object.
   *
   * @author edgar
   */
  class Resolver {
    private String startDelim = "${";

    private String endDelim = "}";

    private PropertySource source;

    private boolean ignoreMissing;

    /**
     * Set property source.
     *
     * @param source Source.
     * @return This resolver.
     */
    public Resolver source(final Map<String, Object> source) {
      return source(new MapSource(source));
    }

    /**
     * Set property source.
     *
     * @param source Source.
     * @return This resolver.
     */
    public Resolver source(final PropertySource source) {
      this.source = source;
      return this;
    }

    /**
     * Set property source.
     *
     * @param source Source.
     * @return This resolver.
     */
    public Resolver source(final Config source) {
      return source(new ConfigSource(source));
    }

    /**
     * Set start and end delimiters.
     *
     * @param start Start delimiter.
     * @param end End delimiter.
     * @return This resolver.
     */
    public Resolver delimiters(final String start, final String end) {
      this.startDelim = requireNonNull(start, "Start delimiter required.");
      this.endDelim = requireNonNull(end, "End delmiter required.");
      return this;
    }

    /**
     * Ignore missing property replacement and leave the expression untouch.
     *
     * @return This resolver.
     */
    public Resolver ignoreMissing() {
      this.ignoreMissing = true;
      return this;
    }

    /**
     * Returns a string with all substitutions (the <code>${foo.bar}</code> syntax,
     * see <a href="https://github.com/typesafehub/config/blob/master/HOCON.md">the
     * spec</a>) resolved. Substitutions are looked up using the <code>source</code> param as the
     * root object, that is, a substitution <code>${foo.bar}</code> will be replaced with
     * the result of <code>getValue("foo.bar")</code>.
     *
     * @param text Text to process.
     * @return A processed string.
     */
    public String resolve(final String text) {
      requireNonNull(text, "Text is required.");
      if (text.length() == 0) {
        return "";
      }

      BiFunction<Integer, BiFunction<Integer, Integer, RuntimeException>, RuntimeException> err = (
          start, ex) -> {
        String snapshot = text.substring(0, start);
        int line = Splitter.on('\n').splitToList(snapshot).size();
        int column = start - snapshot.lastIndexOf('\n');
        return ex.apply(line, column);
      };

      StringBuilder buffer = new StringBuilder();
      int offset = 0;
      int start = text.indexOf(startDelim);
      while (start >= 0) {
        int end = text.indexOf(endDelim, start + startDelim.length());
        if (end == -1) {
          throw err.apply(start, (line, column) -> new IllegalArgumentException(
              "found '" + startDelim + "' expecting '" + endDelim + "' at " + line + ":"
                  + column));
        }
        buffer.append(text.substring(offset, start));
        String key = text.substring(start + startDelim.length(), end);
        Object value;
        try {
          value = source.get(key);
        } catch (NoSuchElementException x) {
          if (ignoreMissing) {
            value = text.substring(start, end + endDelim.length());
          } else {
            throw err.apply(start, (line, column) -> new NoSuchElementException(
                "Missing " + startDelim + key + endDelim + " at " + line + ":" + column));
          }
        }
        buffer.append(value);
        offset = end + endDelim.length();
        start = text.indexOf(startDelim, offset);
      }
      if (buffer.length() == 0) {
        return text;
      }
      if (offset < text.length()) {
        buffer.append(text.substring(offset));
      }
      return buffer.toString();
    }
  }

  /**
   * Utility class for generating {@link Key} for named services.
   *
   * @author edgar
   */
  class ServiceKey {
    private Map<Object, Integer> instances = new HashMap<>();

    /**
     * Generate at least one named key for the provided type. If this is the first call for the
     * provided type then it generates an unnamed key.
     *
     * @param type Service type.
     * @param name Service name.
     * @param keys Key callback. Invoked once with a named key, and optionally again with an unamed
     *        key.
     * @param <T> Service type.
     */
    public <T> void generate(final Class<T> type, final String name, final Consumer<Key<T>> keys) {
      Integer c = instances.put(type, instances.getOrDefault(type, 0) + 1);
      if (c == null) {
        // def key
        keys.accept(Key.get(type));
      }
      keys.accept(Key.get(type, Names.named(name)));
    }
  }

  /**
   * Build an jooby environment.
   *
   * @author edgar
   */
  interface Builder {

    /**
     * Build a new environment from a {@link Config} object. The environment is created from the
     * <code>application.env</code> property. If such property is missing, env's name must be:
     * <code>dev</code>.
     *
     * Please note an environment created with this method won't have a {@link Env#router()}.
     *
     * @param config A config instance.
     * @return A new environment.
     */
    @Nonnull
    default Env build(final Config config) {
      return build(config, null, Locale.getDefault());
    }

    /**
     * Build a new environment from a {@link Config} object. The environment is created from the
     * <code>application.env</code> property. If such property is missing, env's name must be:
     * <code>dev</code>.
     *
     * @param config A config instance.
     * @param router Application router.
     * @param locale App locale.
     * @return A new environment.
     */
    @Nonnull
    Env build(Config config, @Nullable Router router, Locale locale);
  }

  /**
   * Default builder.
   */
  Env.Builder DEFAULT = (config, router, locale) -> {
    requireNonNull(config, "Config required.");
    String name = config.hasPath("application.env") ? config.getString("application.env") : "dev";
    return new Env() {

      private ImmutableList.Builder<Throwing.Consumer<Registry>> start = ImmutableList.builder();

      private ImmutableList.Builder<Throwing.Consumer<Registry>> started = ImmutableList.builder();

      private ImmutableList.Builder<Throwing.Consumer<Registry>> shutdown = ImmutableList.builder();

      private Map<String, Function<String, String>> xss = new HashMap<>();

      private Map<Object, Object> globals = new HashMap<>();

      private ServiceKey key = new ServiceKey();

      public <T> Env set(Key<T> key, T value) {
        globals.put(key, value);
        return this;
      }

      public <T> T unset(Key<T> key) {
        return (T) globals.remove(key);
      }

      public <T> Optional<T> get(Key<T> key) {
        T value = (T) globals.get(key);
        return Optional.ofNullable(value);
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public ServiceKey serviceKey() {
        return key;
      }

      @Override
      public Router router() {
        if (router == null) {
          throw new UnsupportedOperationException();
        }
        return router;
      }

      @Override
      public Config config() {
        return config;
      }

      @Override
      public Locale locale() {
        return locale;
      }

      @Override
      public String toString() {
        return name();
      }

      @Override
      public List<Throwing.Consumer<Registry>> stopTasks() {
        return shutdown.build();
      }

      @Override
      public Env onStop(final Throwing.Consumer<Registry> task) {
        this.shutdown.add(task);
        return this;
      }

      @Override
      public Env onStart(final Throwing.Consumer<Registry> task) {
        this.start.add(task);
        return this;
      }

      @Override
      public LifeCycle onStarted(final Throwing.Consumer<Registry> task) {
        this.started.add(task);
        return this;
      }

      @Override
      public List<Throwing.Consumer<Registry>> startTasks() {
        return this.start.build();
      }

      @Override
      public List<Throwing.Consumer<Registry>> startedTasks() {
        return this.started.build();
      }

      @Override
      public Map<String, Function<String, String>> xss() {
        return Collections.unmodifiableMap(xss);
      }

      @Override
      public Env xss(final String name, final Function<String, String> escaper) {
        xss.put(requireNonNull(name, "Name required."),
            requireNonNull(escaper, "Function required."));
        return this;
      }
    };
  };

  /**
   * @return Env's name.
   */
  @Nonnull
  String name();

  /**
   * Application router.
   *
   * @return Available {@link Router}.
   * @throws UnsupportedOperationException if router isn't available.
   */
  @Nonnull
  Router router() throws UnsupportedOperationException;

  /**
   * @return environment properties.
   */
  @Nonnull
  Config config();

  /**
   * @return Default locale from <code>application.lang</code>.
   */
  @Nonnull
  Locale locale();

  /**
   * @return Utility method for generating keys for named services.
   */
  @Nonnull
  default ServiceKey serviceKey() {
    return new ServiceKey();
  }

  /**
   * Returns a string with all substitutions (the <code>${foo.bar}</code> syntax,
   * see <a href="https://github.com/typesafehub/config/blob/master/HOCON.md">the
   * spec</a>) resolved. Substitutions are looked up using the {@link #config()} as the root object,
   * that is, a substitution <code>${foo.bar}</code> will be replaced with
   * the result of <code>getValue("foo.bar")</code>.
   *
   * @param text Text to process.
   * @return A processed string.
   */
  @Nonnull
  default String resolve(final String text) {
    return resolver().resolve(text);
  }

  /**
   * Creates a new environment {@link Resolver}.
   *
   * @return A resolver object.
   */
  @Nonnull
  default Resolver resolver() {
    return new Resolver().source(config());
  }

  /**
   * Runs the callback function if the current env matches the given name.
   *
   * @param name A name to test for.
   * @param fn A callback function.
   * @param <T> A resulting type.
   * @return A resulting object.
   */
  @Nonnull
  default <T> Optional<T> ifMode(final String name, final Supplier<T> fn) {
    if (name().equals(name)) {
      return Optional.of(fn.get());
    }
    return Optional.empty();
  }

  /**
   * @return XSS escape functions.
   */
  @Nonnull
  Map<String, Function<String, String>> xss();

  /**
   * Get or chain the required xss functions.
   *
   * @param xss XSS to combine.
   * @return Chain of required xss functions.
   */
  @Nonnull
  default Function<String, String> xss(final String... xss) {
    Map<String, Function<String, String>> fn = xss();
    BinaryOperator<Function<String, String>> reduce = Function::andThen;
    return Arrays.asList(xss)
        .stream()
        .map(fn::get)
        .filter(Objects::nonNull)
        .reduce(Function.identity(), reduce);
  }

  /**
   * Set/override a XSS escape function.
   *
   * @param name Escape's name.
   * @param escaper Escape function.
   * @return This environment.
   */
  @Nonnull
  Env xss(String name, Function<String, String> escaper);

  /**
   * @return List of start tasks.
   */
  @Nonnull
  List<Throwing.Consumer<Registry>> startTasks();

  /**
   * @return List of start tasks.
   */
  @Nonnull
  List<Throwing.Consumer<Registry>> startedTasks();

  /**
   * @return List of stop tasks.
   */
  @Nonnull
  List<Throwing.Consumer<Registry>> stopTasks();

  /**
   * Add a global object.
   *
   * @param key Object key.
   * @param value Object value.
   * @param <T> Object type.
   * @return This environment.
   */
  @Nonnull
  <T> Env set(Key<T> key, T value);

  /**
   * Add a global object.
   *
   * @param key Object key.
   * @param value Object value.
   * @param <T> Object type.
   * @return This environment.
   */
  @Nonnull
  default <T> Env set(Class<T> key, T value) {
    return set(Key.get(key), value);
  }

  /**
   * Remove a global object.
   *
   * @param key Object key.
   * @param <T> Object type.
   * @return Object value might be null.
   */
  @Nullable <T> T unset(Key<T> key);

  /**
   * Get an object by key or empty when missing.
   *
   * @param key Object key.
   * @param <T> Object type.
   * @return Object valur or empty.
   */
  @Nonnull
  <T> Optional<T> get(Key<T> key);

  /**
   * Get an object by key or empty when missing.
   *
   * @param key Object key.
   * @param <T> Object type.
   * @return Object valur or empty.
   */
  @Nonnull
  default <T> Optional<T> get(Class<T> key) {
    return get(Key.get(key));
  }
}
