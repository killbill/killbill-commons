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
package org.jooby.test;

import static java.util.Objects.requireNonNull;
import org.jooby.funzy.Try;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility test class for mocks. Internal use only.
 *
 * Rewritten from EasyMock+PowerMock to pure Mockito 5 (inline mock maker).
 * See jooby/1-7-easymock-migration.md for migration details.
 *
 * @author edgar
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class MockUnit {

  private static class ConstructorArgCapture {
    final Class<?> captureType;
    Object value;
    boolean captured;

    ConstructorArgCapture(final Class<?> captureType) {
      this.captureType = captureType;
    }
  }

  public class ConstructorBuilder<T> {

    private Class<T> type;

    public ConstructorBuilder(final Class<T> type) {
      this.type = type;
    }

    public T build(final Object... args) {
      // Clear any pending Mockito matchers registered by capture() calls in args
      try {
        org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress()
            .getArgumentMatcherStorage().pullLocalizedMatchers();
      } catch (Exception ignored) {
      }
      T mock = Mockito.mock(type);
      constructorPreMocks.computeIfAbsent(type, k -> new ArrayList<>()).add(mock);
      // Drain pending constructor captures and associate with this constructor type
      if (!pendingConstructorCaptures.isEmpty()) {
        constructorArgCaptures.computeIfAbsent(type, k -> new ArrayList<>())
            .addAll(pendingConstructorCaptures);
        pendingConstructorCaptures.clear();
      }
      return mock;
    }

    public ConstructorBuilder<T> args(final Class... types) {
      // Argument types are not needed for Mockito's mockConstruction
      return this;
    }

  }

  public interface Block {

    void run(MockUnit unit) throws Throwable;

  }

  private List<Object> mocks = new LinkedList<>();

  private Map<Class, List<Object>> globalMock = new LinkedHashMap<>();

  private Map<Class, List<ArgumentCaptor<Object>>> captures = new LinkedHashMap<>();

  // Constructor arg captures keyed by constructor type (populated by build())
  private Map<Class, List<ConstructorArgCapture>> constructorArgCaptures = new LinkedHashMap<>();

  // Pending captures waiting to be claimed by the next build() call
  private List<ConstructorArgCapture> pendingConstructorCaptures = new ArrayList<>();

  // Static mocks: type → MockedStatic (opened during expect block execution)
  private Map<Class, MockedStatic> staticMocks = new LinkedHashMap<>();

  // Constructor mocks: type → list of pre-configured mocks (in order)
  private Map<Class, List<Object>> constructorPreMocks = new LinkedHashMap<>();

  // Opened MockedConstruction instances (closed in run())
  private List<MockedConstruction<?>> constructionMocks = new LinkedList<>();

  // Maps constructed mock → pre-configured mock (for delegation)
  private Map<Object, Object> mockToPreMock = new IdentityHashMap<>();

  // Reverse: maps pre-configured mock → constructed mock (for identity in get())
  private Map<Object, Object> preMockToConstructed = new IdentityHashMap<>();

  // Void method captures: type → list of captured values (populated via doAnswer in tests)
  private Map<Class, List<Object>> voidCaptures = new LinkedHashMap<>();

  private List<Block> blocks = new LinkedList<>();

  public MockUnit(final Class... types) {
    this(false, types);
  }

  public MockUnit(final boolean strict, final Class... types) {
    Arrays.stream(types).forEach(this::registerMock);
  }

  public <T> T capture(final Class<T> type) {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    captures.computeIfAbsent(type, k -> new ArrayList<>()).add(captor);
    // Register a pending constructor capture (build() will drain these)
    pendingConstructorCaptures.add(new ConstructorArgCapture(type));
    return (T) captor.capture();
  }

  /**
   * Record a value captured from a void method via doAnswer().
   * Use with: doAnswer(inv -> { unit.addVoidCapture(Type.class, inv.getArgument(0)); return null; })
   *           .when(mock).voidMethod(any());
   */
  public <T> void addVoidCapture(final Class<T> type, final Object value) {
    voidCaptures.computeIfAbsent(type, k -> new ArrayList<>()).add(value);
  }

  public <T> List<T> captured(final Class<T> type) {
    List<T> result = new LinkedList<>();
    // From ArgumentCaptors (when() stubbing contexts)
    List<ArgumentCaptor<Object>> captorList = this.captures.get(type);
    if (captorList != null) {
      captorList.forEach(c -> {
        try {
          result.add((T) c.getValue());
        } catch (Exception ignored) {
          // captor not yet captured
        }
      });
    }
    // From constructor arg captures (build() contexts)
    for (List<ConstructorArgCapture> caps : constructorArgCaptures.values()) {
      for (ConstructorArgCapture cap : caps) {
        if (cap.captured && cap.captureType.equals(type)) {
          result.add((T) cap.value);
        }
      }
    }
    // From void method captures (doAnswer() contexts)
    List<Object> voidList = voidCaptures.get(type);
    if (voidList != null) {
      voidList.forEach(v -> result.add((T) v));
    }
    return result;
  }

  public <T> MockedStatic<T> mockStatic(final Class<T> type) {
    MockedStatic<T> ms = (MockedStatic<T>) staticMocks.get(type);
    if (ms == null) {
      ms = Mockito.mockStatic(type);
      staticMocks.put(type, ms);
    }
    return ms;
  }

  public <T> MockedStatic<T> mockStaticPartial(final Class<T> type, final String... names) {
    // Mockito mockStatic mocks all static methods; callers stub the specific ones they need
    return mockStatic(type);
  }

  public <T> T partialMock(final Class<T> type, final String... methods) {
    // Mockito doesn't have direct partial mock equivalent;
    // use spy() for real-method-by-default or mock() for mock-by-default
    T mock = Mockito.mock(type, Mockito.CALLS_REAL_METHODS);
    mocks.add(mock);
    return mock;
  }

  public <T> T partialMock(final Class<T> type, final String method, final Class<?> firstArg) {
    return partialMock(type, method);
  }

  public <T> T partialMock(final Class<T> type, final String method, final Class t1,
      final Class t2) {
    return partialMock(type, method);
  }

  public <T> T mock(final Class<T> type) {
    return mock(type, false);
  }

  public <T> T powerMock(final Class<T> type) {
    // Mockito 5 inline mock maker handles final classes natively
    return mock(type);
  }

  public <T> T mock(final Class<T> type, final boolean strict) {
    T mock = Mockito.mock(type);
    mocks.add(mock);
    return mock;
  }

  public <T> T registerMock(final Class<T> type) {
    T mock = mock(type);
    globalMock.computeIfAbsent(type, k -> new ArrayList<>()).add(mock);
    return mock;
  }

  public <T> T registerMock(final Class<T> type, final T mock) {
    globalMock.computeIfAbsent(type, k -> new ArrayList<>()).add(mock);
    return mock;
  }

  public <T> T get(final Class<T> type) {
    try {
      List<Object> collection = requireNonNull(globalMock.get(type), "Mock not found: " + type);
      Object result = collection.get(collection.size() - 1);
      // If this is a pre-mock that has been replaced by a construction mock, return the latter
      Object constructed = preMockToConstructed.get(result);
      return (T) (constructed != null ? constructed : result);
    } catch (ArrayIndexOutOfBoundsException ex) {
      throw new IllegalStateException("Not found: " + type);
    }
  }

  public <T> T first(final Class<T> type) {
    List<Object> collection = requireNonNull(globalMock.get(type),
        "Mock not found: " + type);
    Object result = collection.get(0);
    Object constructed = preMockToConstructed.get(result);
    return (T) (constructed != null ? constructed : result);
  }

  public MockUnit expect(final Block block) {
    blocks.add(requireNonNull(block, "A block is required."));
    return this;
  }

  public MockUnit run(final Block block) throws Exception {
    return run(new Block[]{block});
  }

  public MockUnit run(final Block... blocks) throws Exception {
    try {
      // 1. Execute expect blocks (configures stubs — active immediately in Mockito)
      for (Block block : this.blocks) {
        Try.run(() -> block.run(this))
            .throwException();
      }

      // 2. Open MockedConstruction for all registered constructor types
      openConstructionMocks();

      // 3. Execute test blocks
      for (Block main : blocks) {
        Try.run(() -> main.run(this)).throwException();
      }
    } finally {
      // 4. Close all scoped mocks (MockedStatic, MockedConstruction)
      closeAll();
    }

    return this;
  }

  public <T> T mockConstructor(final Class<T> type, final Class<?>[] paramTypes,
      final Object... args) {
    // Clear any pending Mockito matchers registered by capture() calls in args
    try {
      org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress()
          .getArgumentMatcherStorage().pullLocalizedMatchers();
    } catch (Exception ignored) {
    }
    T mock = Mockito.mock(type);
    constructorPreMocks.computeIfAbsent(type, k -> new ArrayList<>()).add(mock);
    // Drain pending constructor captures and associate with this constructor type
    if (!pendingConstructorCaptures.isEmpty()) {
      constructorArgCaptures.computeIfAbsent(type, k -> new ArrayList<>())
          .addAll(pendingConstructorCaptures);
      pendingConstructorCaptures.clear();
    }
    return mock;
  }

  public <T> T mockConstructor(final Class<T> type, final Object... args) {
    return mockConstructor(type, null, args);
  }

  public <T> ConstructorBuilder<T> constructor(final Class<T> type) {
    return new ConstructorBuilder<>(type);
  }

  private void openConstructionMocks() {
    for (Map.Entry<Class, List<Object>> entry : constructorPreMocks.entrySet()) {
      Class type = entry.getKey();
      List<Object> preMocks = entry.getValue();
      AtomicInteger counter = new AtomicInteger(0);

      MockedConstruction mc = Mockito.mockConstruction(type,
          Mockito.withSettings().defaultAnswer(invocation -> {
            Object preMock = mockToPreMock.get(invocation.getMock());
            if (preMock != null) {
              try {
                java.lang.reflect.Method method = invocation.getMethod();
                method.setAccessible(true);
                return method.invoke(preMock, invocation.getArguments());
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            }
            return Mockito.RETURNS_DEFAULTS.answer(invocation);
          }),
          (mock, context) -> {
            int i = counter.getAndIncrement();
            if (i < preMocks.size()) {
              Object preMock = preMocks.get(i);
              mockToPreMock.put(mock, preMock);
              preMockToConstructed.put(preMock, mock);
            }
            // Populate constructor arg captures with actual constructor arguments
            List<ConstructorArgCapture> caps = constructorArgCaptures.get(type);
            if (caps != null) {
              List<?> args = context.arguments();
              for (int j = 0; j < Math.min(caps.size(), args.size()); j++) {
                caps.get(j).value = args.get(j);
                caps.get(j).captured = true;
              }
            }
          });
      constructionMocks.add(mc);
    }
  }

  private void closeAll() {
    for (MockedConstruction<?> mc : constructionMocks) {
      mc.close();
    }
    constructionMocks.clear();

    for (MockedStatic<?> ms : staticMocks.values()) {
      ms.close();
    }
    staticMocks.clear();
  }

}
