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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TryTest {

  static class Resource implements AutoCloseable {

    final CountDownLatch closer;

    public Resource(CountDownLatch closer) {
      this.closer = closer;
    }

    @Override public void close() throws Exception {
      closer.countDown();
    }
  }

  static class Conn extends Resource {
    public Conn(final CountDownLatch closer) {
      super(closer);
    }

    public PreparedStatemet preparedStatemet(String sql) {
      return new PreparedStatemet(closer);
    }
  }

  static class PreparedStatemet extends Resource {
    public PreparedStatemet(final CountDownLatch closer) {
      super(closer);
    }

    public ResultSet executeQuery() {
      return new ResultSet(closer);
    }
  }

  static class ResultSet extends Resource {
    public ResultSet(final CountDownLatch closer) {
      super(closer);
    }

    public String next() {
      return "OK";
    }
  }

  @Test
  public void apply() {
    AtomicReference<String> callback = new AtomicReference<>();
    Try.Value<String> value = Try.apply(() -> "OK")
        .onSuccess(callback::set);
    assertEquals(true, value.isSuccess());
    assertEquals(false, value.isFailure());
    assertEquals("OK", value.get());
    assertEquals("OK", callback.get());
  }

  @Test
  public void applyWithFailure() {
    AtomicReference<Throwable> callback = new AtomicReference<>();
    Try value = Try.apply(() -> {
      throw new IllegalArgumentException("Catch me");
    }).onFailure(callback::set);
    assertEquals(false, value.isSuccess());
    assertEquals(true, value.isFailure());
    assertEquals(value.getCause().get(), callback.get());
  }

  @Test
  public void applyWithRecover() {
    Function<Throwable, Try.Value<String>> factory = x -> {
      return Try.apply(() -> {
        throw x;
      });
    };
    assertEquals("x",
        factory.apply(new Throwable("intentional err")).recover(Throwable.class, "x").get());

    assertEquals("ex",
        factory.apply(new Throwable("intentional err")).recover(Throwable.class, x -> "ex").get());
    assertEquals("OK",
        factory.apply(new Throwable("intentional err")).recover(x -> "OK").get());

    assertEquals("ex",
        factory.apply(new Throwable("intentional err")).orElse("ex"));
    assertEquals("exGet",
        factory.apply(new Throwable("intentional err")).orElseGet(() -> "exGet"));
  }

  @Test
  public void run() {
    AtomicInteger counter = new AtomicInteger();
    Try run = Try.run(() -> {
    }).onSuccess(() -> counter.incrementAndGet());
    assertEquals(true, run.isSuccess());
    assertEquals(false, run.isFailure());
    assertEquals(1, counter.get());
  }

  @Test
  public void tryResource1() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    String applyResult = Try.with(() -> new Resource((latch)))
        .apply(r -> "OK")
        .get();
    latch.await();
    assertEquals("OK", applyResult);

  }

  @Test
  public void tryResourceObject1() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    Try.of(new Resource(latch))
        .apply(in -> in.toString())
        .get();
    latch.await();
  }

  @Test
  public void tryResourceMap() throws InterruptedException {
    CountDownLatch counter = new CountDownLatch(4);

    Try.of(new Conn(counter))
        .map(c -> c.preparedStatemet("..."))
        .map(s -> s.executeQuery())
        .apply(rs -> {
          assertEquals(4L, counter.getCount());
          return rs.next();
        })
        .onComplete(() -> {
          assertEquals(1L, counter.getCount());
          counter.countDown();
        })
        .get();

    counter.await();
  }

  @Test
  public void tryResource2() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(2);
    String output = Try.with(() -> new Resource(latch), () -> new Resource(latch))
        .apply((in, out) -> in.getClass().getSimpleName() + out.getClass().getSimpleName())
        .get();
    latch.await();
    assertEquals("ResourceResource", output);
  }

  @Test
  public void runWithFailure() {
    AtomicInteger counter = new AtomicInteger();
    Try run = Try.run(() -> {
      throw new IllegalArgumentException();
    }).onFailure(x -> {
      assertNotNull(x);
      counter.incrementAndGet();
    });
    assertEquals(false, run.isSuccess());
    assertEquals(true, run.isFailure());
    assertEquals(1, counter.get());
  }

  @Test(expected = IllegalArgumentException.class)
  public void unwrap() {
    Try.apply(() -> {
      throw new InvocationTargetException(new IllegalArgumentException());
    })
        .unwrap(InvocationTargetException.class)
        .get();
  }

  @Test
  public void unwrapValue() {
    String value = Try.apply(() -> "OK")
        .unwrap(InvocationTargetException.class)
        .get();
    assertEquals("OK", value);
  }
}
