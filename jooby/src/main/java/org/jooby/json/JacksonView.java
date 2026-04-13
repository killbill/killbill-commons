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
package org.jooby.json;

/**
 * Dynamic jackson view support. Usage:
 *
 * <pre>{@code
 *
 * {
 *   use(new Jackson());
 *
 *   get("/public", req -> {
 *     Item item = ...;
 *     return new JacksonView<Item>(Views.Public.class, item);
 *   });
 * }
 *
 * }</pre>
 */
public class JacksonView<T> {

  /** View/projection class. */
  public final Class view;

  /** Data/payload. */
  public final T data;

  /**
   * Creates a new jackson view.
   *
   * @param view View/projection class.
   * @param data Data/payload.
   */
  public JacksonView(final Class view, final T data) {
    this.view = view;
    this.data = data;
  }
}
