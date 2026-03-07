# Phase 1.7 — EasyMock + PowerMock → Mockito Migration

> This document tracks the migration of all test files from EasyMock+PowerMock to pure Mockito 5.
> After migration, `easymock` and all PowerMock references are removed — only `mockito-core` remains.

---

## Background

- 76 test files in `src/test/java-excluded/` depend on EasyMock/PowerMock/external HTTP clients.
- `MockUnit.java` is the central test utility — wraps EasyMock's record-replay lifecycle and PowerMock's static/constructor mocking.
- Mockito 5.3.1 (managed by `killbill-oss-parent`) provides `mockStatic()` and `mockConstruction()` natively.

## Migration Strategy

Replace the EasyMock record-replay pattern:
```java
// BEFORE (EasyMock + PowerMock)
EasyMock.expect(mock.foo()).andReturn(value);
EasyMock.replay(mock);
// ... test code ...
EasyMock.verify(mock);
```

With Mockito's stubbing pattern:
```java
// AFTER (Mockito)
when(mock.foo()).thenReturn(value);
// ... test code ...
verify(mock).foo();
```

Key API mappings:

| EasyMock / PowerMock | Mockito 5 |
|---|---|
| `EasyMock.createMock(Foo.class)` | `Mockito.mock(Foo.class)` |
| `EasyMock.expect(mock.foo()).andReturn(val)` | `when(mock.foo()).thenReturn(val)` |
| `EasyMock.expect(mock.foo()).andThrow(ex)` | `when(mock.foo()).thenThrow(ex)` |
| `EasyMock.expectLastCall()` | `doNothing().when(mock).foo()` or just call the void method |
| `EasyMock.expectLastCall().andThrow(ex)` | `doThrow(ex).when(mock).foo()` |
| `EasyMock.replay(mock)` | *(not needed — stubs are active immediately)* |
| `EasyMock.verify(mock)` | `verify(mock).foo()` *(per-method, or omit if not needed)* |
| `EasyMock.capture()` | `ArgumentCaptor.forClass(Foo.class)` |
| `EasyMock.isA(Foo.class)` | `any(Foo.class)` |
| `EasyMock.eq(val)` | `eq(val)` |
| `EasyMock.anyObject()` | `any()` |
| `PowerMock.mockStatic(Foo.class)` | `Mockito.mockStatic(Foo.class)` (try-with-resources) |
| `PowerMock.createMockAndExpectNew(Foo.class, args)` | `Mockito.mockConstruction(Foo.class)` |
| `@RunWith(PowerMockRunner.class)` | Remove (or `@ExtendWith(MockitoExtension.class)`) |
| `@PrepareForTest({Foo.class})` | Remove |

---

## Sub-Phases

### 1.7.1 — Rewrite MockUnit.java ✅

- **DONE.** `src/test/java/org/jooby/test/MockUnit.java` rewritten to pure Mockito 5.
- Key design decisions:
  - `mock()` / `powerMock()` → `Mockito.mock()` (inline mock maker handles finals).
  - `mockStatic()` → `Mockito.mockStatic()`, returns `MockedStatic<T>`, opened immediately during expect blocks.
  - `mockConstructor()` / `constructor().build()` → creates pre-configured mock; defers `Mockito.mockConstruction()` to `run()` with delegation via `Method.invoke()`.
  - `capture()` → `ArgumentCaptor.forClass()`.
  - `partialMock()` → `Mockito.mock(type, CALLS_REAL_METHODS)`.
  - `run()` lifecycle: execute expect blocks → open construction mocks → execute test blocks → close all scoped mocks.
- Added `mockito-core` (test scope) to `pom.xml`.
- **Validation:** Compiles, 334 existing tests still pass, `mvn install` succeeds.

### 1.7.2 — Migrate Simple MockUnit Tests (unit.mock() only) ✅

- **DONE.** 44 files migrated from EasyMock to Mockito and moved from `java-excluded/` to `java/`.
- Mechanical migration (regex-based script) + manual fixes for 6 files.
- Key issues discovered and resolved:
  - **Sequential return semantic gap:** EasyMock `expect().andReturn("a"); expect().andReturn("b")` is ordered; Mockito `when().thenReturn("a"); when().thenReturn("b")` overrides. Fix: `thenReturn("a", "b")`. Only 1 file (`OptionsHandlerTest`) had this within a single MockUnit block.
  - **Void method arg capturing:** `unit.capture()` in void method context doesn't work with `ArgumentCaptor` (no `when()` wrapper). Fix: explicit `doAnswer()` with `AtomicReference` in SseTest.
  - **Constructor arg capturing:** `unit.capture()` in `build()` context registers orphaned Mockito matchers. Fix: `ConstructorArgCapture` inner class + `ThreadSafeMockingProgress.pullLocalizedMatchers()`.
  - **ByteBuddy corruption:** EasyMock + Mockito coexistence in same JVM corrupts generated `Method` objects (`NullPointerException` at `Method.getParameterTypes()`). Fix: `reuseForks=false` in surefire.
- 1 file (`LogbackConfTest`) deferred — classpath issue, not mock-related.
- **Validation:** 661 tests pass, 0 failures.

### 1.7.3 — Migrate mockStatic Tests

- **12 files** that use `unit.mockStatic()` but NOT `mockConstructor`.
- Mockito's `mockStatic()` returns `MockedStatic<T>` — must be closed (try-with-resources).
- This means `MockUnit.mockStatic()` must manage `MockedStatic` instances and close them in `run()`.
- Move migrated files back to `java/`.
- Validate: tests compile and pass.

### 1.7.4 — Migrate mockConstructor Tests

- **7 files** that use `unit.mockConstructor()` / `unit.constructor()` but NOT `mockStatic`.
- Mockito's `mockConstruction()` returns `MockedConstruction<T>` — must be closed.
- Move migrated files back to `java/`.
- Validate: tests compile and pass.

### 1.7.5 — Migrate Complex Tests (mockStatic + mockConstructor)

- **6 files** that use BOTH `mockStatic` AND `mockConstructor`.
- These are the most complex migration targets.
- Move migrated files back to `java/`.
- Validate: tests compile and pass.

### 1.7.6 — Migrate Remaining Utilities

- **7 non-MockUnit files:**
  - `JoobyRunner.java` — depends on `Client.java`/`ServerFeature.java` (HTTP integration test runner).
  - `JoobySuite.java` — depends on `JoobyRunner.java`.
  - `Client.java` — HTTP client utility, needs Apache HttpClient dep.
  - `ServerFeature.java` — integration test base, needs Apache HttpClient dep.
  - `SseFeature.java` — SSE integration test base, needs Ning Async HTTP Client dep.
  - `JettyHandlerTest.java` — uses removed `WebSocketServerFactory` (Jetty 10 incompatibility, not mock-related).
  - `RequestScopeTest.java` — uses `com.google.inject.internal.CircularDependencyProxy` (Guice internal API, not mock-related).
- Decision needed: do we add HttpClient/Ning as test deps, or defer integration tests?
- Move whatever compiles back to `java/`.
- Validate: tests compile and pass.

### 1.7.7 — Cleanup and Finalize

- Remove `easymock` dependency from `jooby/pom.xml`.
- Add `mockito-core` as test dependency (managed by parent).
- Verify `java-excluded/` is empty (or document why files remain).
- Remove `-Pjooby` profile testExclude workarounds if no longer needed.
- Update `CHANGES.md` with final migration summary.
- Update `killbill-jooby-todo.md` section 7 as ✅.
- Run full `mvn clean install -pl jooby -Pjooby` — all tests pass.
- Run `mvn clean install` (root) — no sibling breakage.

---

## File Inventory

| Category | Count | Status |
|---|---|---|
| MockUnit only (no static/constructor) | 44 | ✅ Migrated (Phase 1.7.2) |
| mockStatic only | 12 | Pending (Phase 1.7.3) |
| mockConstructor only | 7 | Pending (Phase 1.7.4) |
| mockStatic + mockConstructor | 6 | Pending (Phase 1.7.5) |
| Non-MockUnit utilities / other | 7 | Pending (Phase 1.7.6) |
| Remaining in `java-excluded/` | 32 | Sum of above pending phases |

## Progress

- [x] 1.7.1 — Rewrite MockUnit.java
- [x] 1.7.2 — Migrate 44 simple MockUnit tests
- [ ] 1.7.3 — Migrate mockStatic tests
- [ ] 1.7.4 — Migrate mockConstructor tests
- [ ] 1.7.5 — Migrate complex tests (static + constructor)
- [ ] 1.7.6 — Migrate remaining utilities
- [ ] 1.7.7 — Cleanup and finalize
