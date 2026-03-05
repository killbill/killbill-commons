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

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.Map;

import org.jooby.test.MockUnit;
import org.junit.Test;

import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.internal.CircularDependencyProxy;

public class RequestScopeTest {

  @Test
  public void enter() {
    RequestScope requestScope = new RequestScope();
    requestScope.enter(Collections.emptyMap());
    requestScope.exit();
  }

  @SuppressWarnings({"unchecked", "rawtypes" })
  @Test
  public void scopedValue() throws Exception {
    RequestScope requestScope = new RequestScope();
    Key<Object> key = Key.get(Object.class);
    Object value = new Object();
    try {
      new MockUnit(Provider.class, Map.class)
          .expect(unit -> {
            Map scopedObjects = unit.get(Map.class);
            requestScope.enter(scopedObjects);
            expect(scopedObjects.get(key)).andReturn(null);
            expect(scopedObjects.containsKey(key)).andReturn(false);

            expect(scopedObjects.put(key, value)).andReturn(null);
          })
          .expect(unit -> {
            Provider provider = unit.get(Provider.class);
            expect(provider.get()).andReturn(value);
          })
          .run(unit -> {
            Object result = requestScope.<Object> scope(key, unit.get(Provider.class)).get();
            assertEquals(value, result);
          });
    } finally {
      requestScope.exit();
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes" })
  @Test
  public void scopedNullValue() throws Exception {
    RequestScope requestScope = new RequestScope();
    Key<Object> key = Key.get(Object.class);
    try {
      new MockUnit(Provider.class, Map.class)
          .expect(unit -> {
            Map scopedObjects = unit.get(Map.class);
            requestScope.enter(scopedObjects);
            expect(scopedObjects.get(key)).andReturn(null);
            expect(scopedObjects.containsKey(key)).andReturn(true);
          })
          .run(unit -> {
            Object result = requestScope.<Object> scope(key, unit.get(Provider.class)).get();
            assertEquals(null, result);
          });
    } finally {
      requestScope.exit();
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes" })
  @Test
  public void scopeExistingValue() throws Exception {
    RequestScope requestScope = new RequestScope();
    Key<Object> key = Key.get(Object.class);
    Object value = new Object();
    try {
      new MockUnit(Provider.class, Map.class)
          .expect(unit -> {
            Map scopedObjects = unit.get(Map.class);
            requestScope.enter(scopedObjects);
            expect(scopedObjects.get(key)).andReturn(value);
          })
          .run(unit -> {
            Object result = requestScope.<Object> scope(key, unit.get(Provider.class)).get();
            assertEquals(value, result);
          });
    } finally {
      requestScope.exit();
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes" })
  @Test
  public void circularScopedValue() throws Exception {
    RequestScope requestScope = new RequestScope();
    Key<Object> key = Key.get(Object.class);
    try {
      new MockUnit(Provider.class, Map.class, CircularDependencyProxy.class)
          .expect(unit -> {
            Map scopedObjects = unit.get(Map.class);
            requestScope.enter(scopedObjects);
            expect(scopedObjects.get(key)).andReturn(null);
            expect(scopedObjects.containsKey(key)).andReturn(false);
          })
          .expect(unit -> {
            Provider provider = unit.get(Provider.class);
            expect(provider.get()).andReturn(unit.get(CircularDependencyProxy.class));
          })
          .run(unit -> {
            Object result = requestScope.<Object> scope(key, unit.get(Provider.class)).get();
            assertEquals(unit.get(CircularDependencyProxy.class), result);
          });
    } finally {
      requestScope.exit();
    }
  }

  @SuppressWarnings({"unchecked" })
  @Test(expected = OutOfScopeException.class)
  public void outOfScope() throws Exception {
    RequestScope requestScope = new RequestScope();
    Key<Object> key = Key.get(Object.class);
    Object value = new Object();
    new MockUnit(Provider.class, Map.class)
        .run(unit -> {
          Object result = requestScope.<Object> scope(key, unit.get(Provider.class)).get();
          assertEquals(value, result);
        });
  }
}
