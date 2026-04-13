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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;

public class RequestScopeTest {

  @Test
  public void enter() {
    RequestScope requestScope = new RequestScope();
    requestScope.enter(Collections.emptyMap());
    requestScope.exit();
  }

  @Test
  public void scopedValue() {
    RequestScope requestScope = new RequestScope();
    Key<Object> key = Key.get(Object.class);
    Object value = new Object();
    Map<Object, Object> scopedObjects = new HashMap<>();
    Provider<Object> provider = () -> value;
    try {
      requestScope.enter(scopedObjects);

      Object result = requestScope.scope(key, provider).get();

      assertSame(value, result);
      assertSame(value, scopedObjects.get(key));
    } finally {
      requestScope.exit();
    }
  }

  @Test
  public void scopedNullValue() {
    RequestScope requestScope = new RequestScope();
    Key<Object> key = Key.get(Object.class);
    Map<Object, Object> scopedObjects = new HashMap<>();
    Provider<Object> provider = () -> {
      throw new AssertionError("provider should not be called");
    };
    try {
      requestScope.enter(scopedObjects);
      scopedObjects.put(key, null);

      Object result = requestScope.scope(key, provider).get();

      assertNull(result);
    } finally {
      requestScope.exit();
    }
  }

  @Test
  public void scopeExistingValue() {
    RequestScope requestScope = new RequestScope();
    Key<Object> key = Key.get(Object.class);
    Object value = new Object();
    Map<Object, Object> scopedObjects = new HashMap<>();
    Provider<Object> provider = () -> {
      throw new AssertionError("provider should not be called");
    };
    try {
      requestScope.enter(scopedObjects);
      scopedObjects.put(key, value);

      Object result = requestScope.scope(key, provider).get();

      assertSame(value, result);
    } finally {
      requestScope.exit();
    }
  }

  @Test
  public void circularScopedValue() {
    RequestScope requestScope = new RequestScope();
    Key<Object> key = Key.get(Object.class);
    Map<Object, Object> scopedObjects = new HashMap<>();
    Object value = circularProxy();
    Provider<Object> provider = () -> value;
    try {
      requestScope.enter(scopedObjects);

      Object result = requestScope.scope(key, provider).get();

      assertSame(value, result);
      assertTrue(com.google.inject.Scopes.isCircularProxy(result));
      assertEquals(Collections.emptyMap(), scopedObjects);
    } finally {
      requestScope.exit();
    }
  }

  @SuppressWarnings("unchecked")
  @Test(expected = OutOfScopeException.class)
  public void outOfScope() {
    RequestScope requestScope = new RequestScope();
    requestScope.<Object>scope(Key.get(Object.class), Object::new).get();
  }

  private static Object circularProxy() {
    try {
      Class<?> handlerType = Class.forName("com.google.inject.internal.DelegatingInvocationHandler");
      Constructor<?> constructor = handlerType.getDeclaredConstructor();
      constructor.setAccessible(true);
      Object handler = constructor.newInstance();

      Class<?> bytecodeGenType = Class.forName("com.google.inject.internal.BytecodeGen");
      Method newCircularProxy =
          bytecodeGenType.getDeclaredMethod("newCircularProxy", Class.class, java.lang.reflect.InvocationHandler.class);
      newCircularProxy.setAccessible(true);
      return newCircularProxy.invoke(null, CircularContract.class, handler);
    } catch (ReflectiveOperationException x) {
      throw new AssertionError(x);
    }
  }

  private interface CircularContract {
  }
}
