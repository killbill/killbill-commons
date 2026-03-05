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

import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import javax.annotation.Nonnull;

/**
 * <h1>service registry</h1>
 * <p>
 * Provides access to services registered by modules or application. The registry is powered by
 * Guice.
 * </p>
 *
 * @author edgar
 * @since 1.0.0.CR3
 */
public interface Registry {

  /**
   * Request a service of the given type.
   *
   * @param type A service type.
   * @param <T> Service type.
   * @return A ready to use object.
   */
  @Nonnull
  default <T> T require(final Class<T> type) {
    return require(Key.get(type));
  }

  /**
   * Request a service of the given type and name.
   *
   * @param name A service name.
   * @param type A service type.
   * @param <T> Service type.
   * @return A ready to use object.
   */
  @Nonnull
  default <T> T require(final String name, final Class<T> type) {
    return require(Key.get(type, Names.named(name)));
  }

  /**
   * Request a service of the given type.
   *
   * @param type A service type.
   * @param <T> Service type.
   * @return A ready to use object.
   */
  @Nonnull
  default <T> T require(final TypeLiteral<T> type) {
    return require(Key.get(type));
  }

  /**
   * Request a service of the given key.
   *
   * @param key A service key.
   * @param <T> Service type.
   * @return A ready to use object.
   */
  @Nonnull
  <T> T require(Key<T> key);

  /**
   * Request a service of a given type by a given name.
   *
   * @param name A service name
   * @param type A service type.
   * @param <T> Service type.
   * @return A ready to use object
   */
  @Nonnull
  default <T> T require(final String name, final TypeLiteral<T> type) {
    return require(Key.get(type, Names.named(name)));
  }

}
