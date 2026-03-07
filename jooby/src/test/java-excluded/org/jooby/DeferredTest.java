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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.concurrent.CountDownLatch;

import org.jooby.test.MockUnit;
import org.junit.Test;

public class DeferredTest {

  @Test
  public void newWithNoInit() throws Exception {
    new Deferred().handler(null, (r, ex) -> {
    });
  }

  @Test
  public void newWithInit0() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    new Deferred(deferred -> {
      assertNotNull(deferred);
      latch.countDown();
    }).handler(null, (r, ex) -> {
    });
    latch.await();
  }

  @Test
  public void newWithInit() throws Exception {
    new MockUnit(Request.class)
        .run(unit -> {
          CountDownLatch latch = new CountDownLatch(1);
          new Deferred((req, deferred) -> {
            assertNotNull(deferred);
            assertEquals(unit.get(Request.class), req);
            latch.countDown();
          }).handler(unit.get(Request.class), (r, ex) -> {
          });
          latch.await();
        });
  }

  @Test
  public void resolve() throws Exception {
    Object value = new Object();
    CountDownLatch latch = new CountDownLatch(1);
    Deferred deferred = new Deferred();
    deferred.handler(null, (result, ex) -> {
      assertFalse(result instanceof Deferred);
      assertEquals(value, result.ifGet().get());
      assertNull(ex);
      latch.countDown();
    });
    deferred.resolve(value);
    latch.await();
  }

  @Test
  public void setResolve() throws Exception {
    Object value = new Object();
    CountDownLatch latch = new CountDownLatch(1);
    Deferred deferred = new Deferred();
    deferred.handler(null, (result, ex) -> {
      assertFalse(result instanceof Deferred);
      assertEquals(value, result.ifGet().get());
      latch.countDown();
    });
    deferred.set(value);
    latch.await();
  }

  @Test
  public void reject() throws Exception {
    Exception cause = new Exception();
    CountDownLatch latch = new CountDownLatch(1);
    Deferred deferred = new Deferred();
    deferred.handler(null, (result, ex) -> {
      assertEquals(cause, ex);
      assertNull(result);
      latch.countDown();
    });
    deferred.reject(cause);
    latch.await();
  }

  @Test
  public void setReject() throws Exception {
    Exception cause = new Exception();
    CountDownLatch latch = new CountDownLatch(1);
    Deferred deferred = new Deferred();
    deferred.handler(null, (result, ex) -> {
      assertEquals(cause, ex);
      assertNull(result);
      latch.countDown();
    });
    deferred.set(cause);
    latch.await();
  }

}
