# killbill-jooby — Changes from Upstream

This file documents all intentional deviations from the upstream Jooby 1.6.9 source.
The fork remains based on that upstream source line; modernization work keeps the existing Kill Bill fork in place.

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
| `Response.java` | `Response.Forwarding.setResetHeadersOnError()`: changed `this.setResetHeadersOnError(value)` → `rsp.setResetHeadersOnError(value)` | Upstream bug — infinite recursion. Every other method in `Forwarding` delegates to `rsp`; this one called `this` by mistake |
| `RoutePattern.java` | Simplified the glob-route regex to remove nested ambiguous quantifiers | Fixes CodeQL ReDoS warning without changing route-matching semantics |
| `PemReader.java` | Simplified PEM block regex whitespace handling from redundant alternation to `\\s+` | Fixes CodeQL ReDoS warning while keeping the same accepted PEM formats |

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
| `com.github.spotbugs:spotbugs-annotations` | not present | **not included** | Not needed; no forked source uses `@SuppressFBWarnings`, and SpotBugs triage uses the exclusion filter instead |
| `org.eclipse.jetty:jetty-alpn-server` | not present | 10.0.16 | Required by `JettyServer.java` for ALPN/HTTP2 support |
| `org.eclipse.jetty.websocket:websocket-jetty-api` | not present (was part of websocket-server) | 10.0.16 | Jetty 10 split WebSocket API into separate artifact |
| `org.eclipse.jetty:jetty-io` | transitive | 10.0.16 (explicit) | Used directly in source; declared explicitly to satisfy dependency:analyze |
| `org.eclipse.jetty:jetty-util` | transitive | 10.0.16 (explicit) | Used directly in source; declared explicitly to satisfy dependency:analyze |
| `javax.inject:javax.inject` | transitive via Guice | managed (explicit) | Used directly in source; declared explicitly to satisfy dependency:analyze |
| `junit:junit` | optional (compile) | compile + optional | Parent forces test scope; explicit compile needed for `JoobyRule` |
| `org.mockito:mockito-core` | not present | 5.3.1 (managed, test) | Sole active mocking framework for the migrated test tree |
| `org.easymock:easymock` | present (test) | **removed** | Replaced by mockito-core in the active test tree |
| `org.apache.httpcomponents:httpclient` | not present | 4.5.14 (test) | Integration test HTTP client |
| `org.apache.httpcomponents:httpcore` | not present | 4.4.16 (test) | Required by httpclient |
| `org.apache.httpcomponents:fluent-hc` | not present | 4.5.14 (test) | `Client.java` fluent Executor API |
| `org.apache.httpcomponents:httpmime` | not present | 4.5.14 (test) | `Client.java` multipart support |

## Structural Changes

| Change | Reason |
|---|---|
| 4 upstream modules + funzy merged into 1 flat module | Kill Bill convention (like `killbill-jdbi`, `killbill-config-magic`) |
| `jooby-netty` excluded | Kill Bill uses Jetty; SSE/WebSocket work via core SPI |
| ASM shade plugin preserved | Relocates `org.objectweb.asm` → `org.jooby.internal.asm` (same as upstream) |
| Jooby tests now run in the default Maven lifecycle | The earlier PowerMock-era gating was removed after all deferred tests were restored into the active test tree |
| 20 test files moved to `src/test/java-excluded/` | Were blocked by PowerMock/missing deps; all 20 have now been restored into the active test tree |
| 124 Java files remain in `src/test/java/` | Active test tree after migration, including shared test utilities; the standard Maven test lifecycle runs 108 test classes / 923 tests successfully |
| SpotBugs exclude filter (`spotbugs-exclude.xml`) | Targeted exclusions for 77 upstream findings (12 bug patterns across 10 categories) triaged as intentional framework patterns or low-risk upstream code |
| Apache RAT exclusions for resources | Resource files (`.conf`, `.xml`, `.properties`, SSL certs) have no license headers |

## Configuration / Resource Changes

None. All resource files (`web.xml`, `jooby.conf`, `server.conf`, SSL certs, `mime.properties`,
test configs) are byte-identical to upstream.

## Test Infrastructure Changes

Upstream tests used EasyMock + PowerMock. The active Kill Bill fork now uses **Mockito 5**
(`mockito-core:5.3.1`) as its sole mocking framework.

### MockUnit Rewrite

`src/test/java/org/jooby/test/MockUnit.java` was rewritten around Mockito 5 APIs instead of the
upstream EasyMock record/replay + PowerMock static/constructor mocking model.

| Old API (EasyMock/PowerMock) | New API (Mockito 5) |
|---|---|
| `EasyMock.createMock()` | `Mockito.mock()` |
| `PowerMock.createMock()` (finals) | `Mockito.mock()` |
| `PowerMock.mockStatic()` + `EasyMock.expect(Static.method())` | `Mockito.mockStatic()` returning `MockedStatic<T>` |
| `PowerMock.createMockAndExpectNew()` / `MockUnit.constructor().build()` | Pre-mock + deferred `Mockito.mockConstruction()` with delegation |
| `EasyMock.capture()` / `captured()` | `ArgumentCaptor.forClass().capture()` / `getValue()` |
| `PowerMock.replay()` / `PowerMock.verify()` | Not needed; Mockito stubs are active immediately |
| `partialMock(type, methods)` | `Mockito.mock(type, CALLS_REAL_METHODS)` |

Notable implementation changes in `MockUnit.java`:
- constructor mocking uses a pre-mock + delegation pattern; constructed mocks delegate back to the
  corresponding pre-mock via reflection
- `ConstructorArgCapture` and pending capture queues preserve constructor argument capture support
- `captured()` now merges values from argument captors, constructor captures, and explicit void captures
- `openConstructionMocks()` uses `setAccessible(true)` for package-private inner-class delegation
- `preMockToConstructed` resolves pre-mock to constructed mock identity when tests compare both forms

### Migrated and Rewritten Tests

All 20 files that had been moved to `src/test/java-excluded/` during the migration are now restored
to the active test tree. `src/test/java-excluded/` is empty.

Notable rewrites and follow-up restorations:

| File | Change | Reason |
|---|---|---|
| `CookieImplTest.java` | Reworked assertions to avoid mocking `System.class` | Mockito cannot mock `java.lang.System` reliably |
| `RequestLoggerTest.java` | Reworked latency assertion and void captures | Avoids `System` mocking and adapts to Mockito void stubbing |
| `DefaultErrHandlerTest.java` | Void captures rewritten with `doAnswer()` | `rsp.send(...)` is a void method |
| `JettyResponseTest.java` | Void captures rewritten with `doAnswer()` | `output.sendContent(...)` is a void method |
| `ServletServletResponseTest.java` | `partialMock(FileChannel.class)` replaced with `mock(FileChannel.class)` | `CALLS_REAL_METHODS` caused close-path failures |
| `FileConfTest.java` | Rewritten as a real filesystem test | Replaces EasyMock + PowerMock constructor/static mocking |
| `LogbackConfTest.java` | Rewritten as a real filesystem/config-driven test | Replaces MockUnit-based lookup stubbing |
| `RequestScopeTest.java` | Rewritten as a direct behavior test | Exercises circular-proxy handling without a compile-time Guice internal type dependency |
| `JettyHandlerTest.java` | Rewritten around current Jetty 10 adapter behavior | Upstream websocket-era expectations no longer matched the fork |
| `JettyServerTest.java` | Rewritten around real `Server`, `ServerConnector`, and `ContextHandler` objects | Replaces removed Jetty 9 websocket factory assumptions |
| `SseFeature.java` | Rewritten to use JDK 11 `HttpClient` | Replaces removed Ning AsyncHttpClient dependency |

### Current Test Baseline

- Jooby tests run in the default Maven lifecycle
- `reuseForks=false` remains configured in Surefire for stable Mockito inline runs
- active test tree: `124` Java files in `src/test/java`
- runnable suite: `108` test classes / `923` tests

### Additional Test-Tree Cleanup

- `ParamConverterTest` and `MutantImplTest` were the last direct EasyMock holdouts; both now use `Mockito.mock()`
- `Issue1087.java` was deleted because it was the only direct `@JsonView` / `jackson-annotations` consumer in the forked test tree
- the direct `jackson-annotations` dependency path was removed from `pom.xml` after `Issue1087.java` was deleted
