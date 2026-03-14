# killbill-jooby — Changes from Upstream

This file documents all intentional deviations from the upstream Jooby 1.6.9 source.

Upstream references:
- Jooby: https://github.com/jooby-project/jooby tag `v1.6.9`, commit `85a50d5e894d14068b2e90a0601481cf52a0abec`
- Funzy: https://github.com/jooby-project/funzy commit `728d743ca348f6f12430ec8735057cf6a1687c0c`

---

## License Header Changes

All 297 `.java` files (172 main + 125 test) had their license headers replaced with the
Kill Bill standard header:
- 171 files: replaced the full Apache License 2.0 text block (202 lines) with Kill Bill 16-line header
- 126 files: prepended Kill Bill header (had no prior header, e.g. funzy sources, test files)

## Java Source Changes

The following files were modified from upstream to adapt to Jetty 10 API changes:

| File | Change | Reason |
|---|---|---|
| `JettyResponse.java` | Added `import java.io.IOException`; wrapped `sender().close()` in try-catch | `HttpOutput.close()` throws `IOException` in Jetty 10 (was unchecked in 9) |
| `JettyPush.java` | Replaced `PushBuilder` usage with no-op + log message | HTTP/2 Server Push (`PushBuilder`) removed in Jetty 10 (deprecated in HTTP/2 spec RFC 9113) |
| `JettyHandler.java` | Removed `WebSocketServerFactory` field/parameter; replaced `Request.MULTIPART_CONFIG_ELEMENT` with string constant; simplified `upgrade()` method | `WebSocketServerFactory` removed in Jetty 10; `MULTIPART_CONFIG_ELEMENT` constant removed from `Request` |
| `JettyServer.java` | Removed `WebSocketPolicy`/`WebSocketServerFactory`/`DecoratedObjectFactory` imports and usage; changed `new SslContextFactory()` → `new SslContextFactory.Server()` | WebSocket API completely restructured in Jetty 10; `SslContextFactory` made abstract with `Server` subclass |

## POM / Dependency Changes

The `jooby/pom.xml` is written from scratch (not a copy of any upstream POM). It merges
dependencies from 4 upstream modules (`jooby`, `jooby-servlet`, `jooby-jetty`, `jooby-jackson`)
into a single flat module under Kill Bill Maven coordinates.

Differences from upstream dependency versions:

| Dependency | Upstream | Kill Bill Fork | Reason |
|---|---|---|---|
| `com.google.inject:guice` | 4.2.0 | 5.1.0 (managed by killbill-oss-parent) | Kill Bill standardized version |
| `com.google.inject.extensions:guice-multibindings` | 4.2.0 | **removed** | `Multibinder` merged into core Guice since 4.2 |
| `org.jooby:funzy` | 0.1.0 (external dep) | **removed** (source inlined) | 3 classes copied into `org.jooby.funzy` package |
| `org.eclipse.jetty:jetty-server` | 9.4.24.v20191120 | 10.0.16 (managed) | Kill Bill standardized version |
| `org.eclipse.jetty.http2:http2-server` | 9.4.24.v20191120 | 10.0.16 | Aligned with jetty-server |
| `org.eclipse.jetty.websocket:websocket-server` | 9.4.24.v20191120 | **removed** | WebSocket factory code removed from Jetty adapter; `websocket-jetty-api` added separately |
| `org.eclipse.jetty:jetty-alpn-openjdk8-server` | 9.4.24.v20191120 | **removed** | Not available in Jetty 10; ALPN is built-in |
| `javax.servlet:javax.servlet-api` | 3.1.0 | `jakarta.servlet:jakarta.servlet-api` 4.0.4 | Kill Bill transitional artifact (still ships `javax.servlet` packages) |
| `org.ow2.asm:asm` | 7.3.1 | 9.7 | Updated for JDK 11+ compatibility |
| `com.google.guava:guava` | 25.1-jre | 31.1-jre (managed) | Kill Bill standardized version |
| `com.typesafe:config` | 1.3.3 | 1.4.2 (managed) | Kill Bill standardized version |
| `org.slf4j:slf4j-api` | 1.7.x | 2.0.9 (managed) | Kill Bill standardized version |
| `org.powermock:powermock-*` | 2.0.0 | **removed** | Not managed by killbill-oss-parent; obsolete for modern JDKs |
| `jakarta.annotation:jakarta.annotation-api` | not present | 1.3.5 (managed) | Added for `@PostConstruct`/`@PreDestroy` in `LifeCycle.java` |
| `com.github.spotbugs:spotbugs-annotations` | not present | **not included** | Will be added in Phase 1.8 (SpotBugs triage) |
| `org.eclipse.jetty:jetty-alpn-server` | not present | 10.0.16 | Required by `JettyServer.java` for ALPN/HTTP2 support |
| `org.eclipse.jetty.websocket:websocket-jetty-api` | not present (was part of websocket-server) | 10.0.16 | Jetty 10 split WebSocket API into separate artifact |
| `org.eclipse.jetty:jetty-io` | transitive | 10.0.16 (explicit) | Used directly in source; declared explicitly to satisfy dependency:analyze |
| `org.eclipse.jetty:jetty-util` | transitive | 10.0.16 (explicit) | Used directly in source; declared explicitly to satisfy dependency:analyze |
| `javax.inject:javax.inject` | transitive via Guice | managed (explicit) | Used directly in source; declared explicitly to satisfy dependency:analyze |
| `junit:junit` | optional (compile) | compile + optional | Parent forces test scope; explicit compile needed for `JoobyRule` |
| `org.mockito:mockito-core` | not present | 5.3.1 (managed, test) | Added for Phase 1.7 EasyMock→Mockito migration |

## Structural Changes

| Change | Reason |
|---|---|
| 4 upstream modules + funzy merged into 1 flat module | Kill Bill convention (like `killbill-jdbi`, `killbill-config-magic`) |
| `jooby-netty` excluded | Kill Bill uses Jetty; SSE/WebSocket work via core SPI |
| ASM shade plugin preserved | Relocates `org.objectweb.asm` → `org.jooby.internal.asm` (same as upstream) |
| Test compilation disabled by default | 76 of 125 test files depend on PowerMock (not available); enabled via `-Pjooby` profile |
| 20 test files moved to `src/test/java-excluded/` | Depend on PowerMock mockConstructor or external HTTP clients; will be restored after Phase 1.7.4-1.7.6 |
| 105 test files remain in `src/test/java/` | 50 pre-existing + 43 migrated (1.7.2) + 12 migrated (1.7.3); compile and run with `-Pjooby` profile (751 tests pass) |
| SpotBugs exclude filter (`spotbugs-exclude.xml`) | Suppresses all upstream SpotBugs findings until Phase 1.8 triage |
| Apache RAT exclusions for resources | Resource files (`.conf`, `.xml`, `.properties`, SSL certs) have no license headers |

## Configuration / Resource Changes

None. All resource files (`web.xml`, `jooby.conf`, `server.conf`, SSL certs, `mime.properties`,
test configs) are byte-identical to upstream.

## Test Framework Migration (Phase 1.7)

Upstream tests use EasyMock + PowerMock. These are being migrated to **Mockito 5** (`mockito-core:5.3.1`).

### Sub-phase 1.7.1 — MockUnit.java Rewrite ✅

`src/test/java/org/jooby/test/MockUnit.java` completely rewritten (not a modification of upstream).
The upstream version used EasyMock record-replay + PowerMock static/constructor mocking.
The new version uses pure Mockito 5 APIs:

| Old API (EasyMock/PowerMock) | New API (Mockito 5) |
|---|---|
| `EasyMock.createMock()` | `Mockito.mock()` |
| `PowerMock.createMock()` (finals) | `Mockito.mock()` (inline mock maker handles finals natively) |
| `PowerMock.mockStatic()` + `EasyMock.expect(Static.method())` | `Mockito.mockStatic()` returning `MockedStatic<T>` |
| `PowerMock.createMockAndExpectNew()` / `MockUnit.constructor().build()` | Pre-mock + deferred `Mockito.mockConstruction()` with delegation |
| `EasyMock.capture()` / `captured()` | `ArgumentCaptor.forClass().capture()` / `getValue()` |
| `PowerMock.replay()` / `PowerMock.verify()` | Not needed — Mockito stubs are active immediately |
| `partialMock(type, methods)` | `Mockito.mock(type, CALLS_REAL_METHODS)` |

Key design: Constructor mocking uses a "pre-mock + delegation" pattern. `build()` creates a Mockito mock
that callers configure with `when()`. At `run()` time, `MockedConstruction` is opened; each constructed mock
delegates all calls to its corresponding pre-mock via `Method.invoke()`.

### Sub-phase 1.7.2 — Simple MockUnit Test Migration ✅

44 test files migrated from EasyMock to Mockito syntax (moved from `java-excluded/` to `src/test/java/`).

**Mechanical changes applied to all 44 files:**
- `EasyMock.expect(x).andReturn(y)` → `Mockito.when(x).thenReturn(y)`
- `EasyMock.expectLastCall()` → removed (void stubs not needed in Mockito)
- `expect().andThrow()` → `when().thenThrow()` / `doThrow().when()`
- `@RunWith(PowerMockRunner.class)` / `@PrepareForTest` annotations removed
- Import replacements: `org.easymock.*` → `org.mockito.*`

**Manual fixes for specific files:**

| File | Change | Reason |
|---|---|---|
| `Issue1087.java` | Removed `EasyMock.aryEq()` wrapper | Void method doesn't need argument matcher |
| `RouteDefinitionTest.java` | Line number assertion `9→24` | Kill Bill license header adds 15 lines |
| `RequestTest.java` | Merged sequential `when().thenReturn()` | Mockito overrides; use `thenReturn(a, b)` for ordered returns |
| `JacksonParserTest.java` | Cast `null` to `(java.lang.reflect.Type)` | Overload disambiguation for `parse(Type)` vs `parse(MediaType)` |
| `OptionsHandlerTest.java` | Created `routeMethods(String...)` varargs helper | Only file with true sequential return pattern within same MockUnit block |
| `SseTest.java` | Rewrote 3 methods with explicit `doAnswer()` captors | Void method arg capturing requires `doAnswer()` instead of `ArgumentCaptor` |

**Files excluded from migration (non-mock issues):**

| File | Reason | Status |
|---|---|---|
| `LogbackConfTest.java` | `NoClassDefFoundError: org/jooby/Jooby` (static init classpath issue) | Remains in `java-excluded/` |

**Surefire configuration changes:**

| Setting | Value | Reason |
|---|---|---|
| `reuseForks` | `false` | EasyMock + Mockito coexistence corrupts ByteBuddy-generated `Method` objects when sharing JVM across test classes |
| `argLine` | `-XX:-OmitStackTraceInFastThrow --illegal-access=permit` | Full stack traces for debugging; JDK 11 module access |

**MockUnit.java changes for Phase 1.7.2:**
- `ConstructorArgCapture` inner class + pending capture queue for `build()` context
- `build()` clears orphaned Mockito matchers via `ThreadSafeMockingProgress.pullLocalizedMatchers()`
- `captured()` merges from ArgumentCaptors + constructor arg captures
- `openConstructionMocks()` populates constructor captures from `context.arguments()`

**Result:** 661 tests pass (327 pre-existing + 334 migrated), 0 failures.

### Sub-phase 1.7.3 — mockStatic Test Migration ✅

12 test files migrated that use `unit.mockStatic()` for static method stubbing.

**Static mock conversion pattern:**
- `unit.mockStatic(X.class); when(X.method(args)).thenReturn(val)` → `unit.mockStatic(X.class).when(() -> X.method(args)).thenReturn(val)`
- No-arg static methods use method reference: `unit.mockStatic(X.class).when(X::method).thenReturn(val)`

**Additional fixes:**

| File | Change | Reason |
|---|---|---|
| `CookieImplTest.java` | Rewrote 2 tests to not mock `System.class` | Mockito cannot mock `java.lang.System` (class loader interference) |
| `RequestLoggerTest.java` | Rewrote `latency` test with regex assertion; void capture → `doAnswer()` | Cannot mock `System.class`; `rsp.complete()` is void |
| `DefaultErrHandlerTest.java` | Void capture → `doAnswer()` with `AtomicReference` | `rsp.send(unit.capture(...))` is void method |
| `JettyResponseTest.java` | Void capture → `doAnswer()` with `AtomicReference` | `output.sendContent(unit.capture(...))` is void method |
| `ServletServletResponseTest.java` | `partialMock(FileChannel.class)` → `mock(FileChannel.class)` | `CALLS_REAL_METHODS` on `FileChannel.close()` causes NPE |
| `CookieSignatureTest.java` | Removed `@PowerMockIgnore` annotation | Not needed in Mockito |

**Result:** 751 tests pass (661 prior + 90 new), 0 failures.

### Remaining sub-phases (in progress)

| Change | Reason |
|---|---|
| `EasyMock.expect().andReturn()` → `when().thenReturn()` | All 68 test files to be converted from EasyMock to Mockito patterns |
| `PowerMock.mockStatic()` → `Mockito.mockStatic()` | 47 static mock calls across 19 files |
| `PowerMock.createMockAndExpectNew()` → `Mockito.mockConstruction()` | 77 constructor mock calls across 17 files |
| `@RunWith(PowerMockRunner.class)` removed | Mockito does not require a custom runner |
| `@PrepareForTest` removed | Mockito handles static/constructor mocking natively |
| `easymock` dependency removed | Fully replaced by `mockito-core` |

See `jooby/1-7-easymock-migration.md` for detailed sub-phase tracking.
