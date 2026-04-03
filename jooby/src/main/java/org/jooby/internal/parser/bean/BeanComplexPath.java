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
package org.jooby.internal.parser.bean;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.List;

@SuppressWarnings("rawtypes")
class BeanComplexPath implements BeanPath {

  private List<BeanPath> chain;

  private BeanPath setter;

  private String path;

  public BeanComplexPath(final List<BeanPath> chain, final BeanPath setter,
      final String path) {
    this.chain = chain;
    this.setter = setter;
    this.path = path;
  }

  @Override
  public void set(final Object bean, final Object... args) throws Throwable {
    Object target = get(bean);
    setter.set(target, args);
  }

  @Override
  public Object get(final Object bean, final Object... args) throws Throwable {
    Object target = bean;
    for (BeanPath path : chain) {
      Object next = path.get(target, args);
      if (next == null) {
        next = ((Class) path.settype()).newInstance();
        path.set(target, next);
      }
      target = next;
    }
    return target;
  }

  @Override
  public AnnotatedElement setelem() {
    return setter.setelem();
  }

  @Override
  public Type settype() {
    return setter.settype();
  }

  @Override
  public Type type() {
    return setter.type();
  }

  @Override
  public String toString() {
    return path;
  }
}