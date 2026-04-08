# Kill Bill Modernization — Full Roadmap

> This document covers both the `killbill-jooby` module (a source fork of Jooby 1.6.9) and
> the broader Jakarta EE / JDK modernization. The sections are ordered by execution sequence —
> each depends on the ones before it. Each section is a **separate branch and PR**.
> Do NOT bundle them.

---

## Phase 0 — Current State Assessment ✅

- The codebase targets **JDK 11** as baseline (`project.build.targetJdk=11` via `killbill-oss-parent:0.146.63`).
- 90 Java source files use `javax.*` imports — zero `jakarta.*` imports exist anywhere.
- Maven coordinates already use transitional `jakarta.*` groupIds for some dependencies, but these still ship `javax.*` packages:
  - `jakarta.servlet:jakarta.servlet-api` in `skeleton`, `metrics` (code uses `javax.servlet.*`).
  - `jakarta.ws.rs:jakarta.ws.rs-api` in `skeleton` (code uses `javax.ws.rs.*`).
  - `jakarta.xml.bind:jakarta.xml.bind-api` in `automaton`, `xmlloader` (code uses `javax.xml.bind.*`).
  - `jakarta.activation:jakarta.activation-api` in `automaton`, `xmlloader`.
- `javax.inject:javax.inject` is still the Maven artifact in 4 modules: `skeleton` (5 files), `queue` (4 files), `jdbi` (2 files), `metrics` (1 file).
- Guice version is **5.1.0** (managed by `killbill-oss-parent`; supports `javax.inject` only).
- Jersey version is **2.39.1** (supports `javax.ws.rs` only; Jersey 3.x requires `jakarta.ws.rs`).
- `javax.servlet` imports span 12 files: `skeleton` (8 files), `metrics` (4 files).
- `javax.ws.rs` imports span 6 files: `skeleton` only.
- `javax.xml.bind` imports span 14 files: `automaton` (7 files), `xmlloader` (7 files).
- `javax.annotation.Nullable`/`@Nonnull` (from `jsr305`) is used in 35 files across 8 modules.
  - This does NOT need migration — `jsr305` is not part of the Jakarta namespace transition.
- `javax.sql` is used in 22 files across 5 modules — this is core JDK, NOT part of Jakarta migration.
- Jooby 1.6.9 uses `javax.*` namespace, which matches the current codebase state.
  - The Jakarta migration keeps the existing `killbill-jooby` fork and updates it in place during Phases 3-4.
- `killbill-commons` already contains vendored forks: `jdbi` (fork of jDBI 2.62) and `config-magic` (fork of config-magic 0.17).
  - `killbill-jooby` follows the same forking pattern.

---

# Phase 1 — Fork Jooby 1.6.9 into killbill-jooby ✅

> Fork the Jooby 1.6.9 source code into `killbill-commons` as a vendored module,
> following the same pattern as `killbill-jdbi` and `killbill-config-magic`.
> No application-level logic — just the forked library repackaged under Kill Bill Maven coordinates.

## 1. Identify Upstream Source Scope ✅

- Fork 5 upstream Jooby 1.6.9 repos/modules: `jooby` (core), `jooby-servlet`, `jooby-jetty`, `jooby-jackson`, and `funzy`.
- **Exclude `jooby-netty`** — SSE (`org.jooby.Sse`) is defined in core as an abstract class; the server runtime (Jetty/Netty) provides the implementation via the `org.jooby.spi.*` SPI, so SSE works fine on Jetty without Netty.
- Netty would pull 8+ artifacts (`netty-transport`, `codec`, `codec-http`, `codec-http2`, `handler`, `common`, `resolver`, `epoll`) plus Javassist and platform-specific profiles — too costly and unnecessary since Kill Bill uses Jetty.
- Note the dependency chain: `jooby-jetty` → `jooby-servlet` → `jooby` (core); all three are required.
- `funzy` is inlined into the fork (3 source + 3 test files under `org.jooby.funzy`) — it's a separate repo (`jooby-project/funzy`, commit `728d743ca348f6f12430ec8735057cf6a1687c0c`) but only 3 classes with zero deps, deeply used in 24 core files.
- Upstream Jooby commit SHA: `85a50d5e894d14068b2e90a0601481cf52a0abec` (tag `v1.6.9`).
- Upstream funzy commit SHA: `728d743ca348f6f12430ec8735057cf6a1687c0c`.
- Inventory: 172 main Java files, 125 test Java files, 6 main resources, 8 test resources, 18 packages — zero conflicts.

## 2. Project Scaffolding ✅

- Created the `jooby/` directory under `killbill-commons` root as a **single flat module** (packaging `jar`), following the `config-magic`/`jdbi` pattern.
- All 5 upstream repos/modules (`jooby` core, `jooby-servlet`, `jooby-jetty`, `jooby-jackson`, `funzy`) merge into this one artifact.
- Created `jooby/pom.xml` with parent `org.kill-bill.commons:killbill-commons:0.27.0-SNAPSHOT` and artifactId `killbill-jooby`.
- Added `jooby` to the `<modules>` list in the root `pom.xml`.
- Added `killbill-jooby` to the root `<dependencyManagement>`.
- This follows the same pattern as `config-magic` and `jdbi` (single flat module).

## 3. Copy Upstream Source ✅

- Copied `jooby/src/` into `jooby/src/`, preserving the original `org.jooby` package structure (core classes).
- Copied `modules/jooby-servlet/src/` into `jooby/src/`, preserving the original `org.jooby.servlet` package structure.
- Copied `modules/jooby-jetty/src/` into `jooby/src/`, preserving the original `org.jooby.jetty` package structure.
- Copied `modules/jooby-jackson/src/` into `jooby/src/`, preserving the original `org.jooby.json` package structure.
- Copied `funzy/src/` into `jooby/src/`, preserving the original `org.jooby.funzy` package structure (3 source + 3 test files).
- All upstream modules use different packages — merging into one `src/` tree caused no package or class name conflicts.
- Copied upstream resource files (`META-INF/web.xml`, `jooby.conf`, `server.conf`, SSL certs, `mime.properties`) into `src/main/resources/`.
- Preserved original copyright/license headers in all copied files (Apache License 2.0).

## 4. Adapt pom.xml Dependencies ✅

- Complete POM written from scratch — see `jooby/CHANGES.md` for the full dependency version mapping table.
- `guice-multibindings` removed (merged into core Guice since 4.2).
- `funzy` removed as external dep (inlined into `org.jooby.funzy`).
- `javax.servlet-api` → `jakarta.servlet:jakarta.servlet-api:4.0.4` (transitional, still ships `javax.servlet`).
- Jetty upgraded from 9.4.24 → 10.0.16; `websocket-server` → `websocket-jetty-server`; `jetty-alpn-server` added.
- ASM shade plugin preserved (`org.objectweb.asm` → `org.jooby.internal.asm`).
- `jakarta.annotation-api:1.3.5` added for `@PostConstruct`/`@PreDestroy`.
- PowerMock removed (obsolete for modern JDKs).
- All other deps aligned to Kill Bill managed versions (Jackson, Guava, SLF4J, Typesafe Config).

## 5. Build Verification (Initial Fork) ✅

- `mvn clean compile -pl jooby` passes with zero errors (8 deprecation warnings from upstream code).
- Fixed 4 Jetty 9→10 API incompatibilities: `JettyServer.java`, `JettyHandler.java`, `JettyPush.java`, `JettyResponse.java` — all documented in `CHANGES.md`.
- HTTP/2 Server Push (`PushBuilder`) stubbed as no-op (removed from Jetty 10 / HTTP/2 spec).
- WebSocket server factory removed from `JettyHandler`/`JettyServer` (Jetty 10 restructured the WebSocket API).
- `SslContextFactory` → `SslContextFactory.Server` (made abstract in Jetty 10).
- All 297 Java files have Kill Bill standard license headers (replacing 202-line Apache headers or prepending to headerless files).
- The remaining test-tree migration work was completed in later Phase 1 steps.

## 6. Configure Upstream Test Handling ✅

- Upstream tests use **JUnit 4** (116 `@Test` imports, 35 `@RunWith` usages).
- 76 test files depended on PowerMock (67 via `MockUnit.java`, 4 test utilities, 5 transitive) and were moved temporarily to `src/test/java-excluded/` during the migration.
- 3 test utility files (`Client.java`, `ServerFeature.java`, `SseFeature.java`) were part of the temporary `java-excluded/` set during the migration; all three are now restored under `src/test/java/`.
- Jooby tests now run in the default Maven lifecycle with `surefire-junit47` and `reuseForks=false`.
- Current active Jooby test baseline: `124` Java files in `src/test/java`, `108` runnable test classes, `923` tests, `src/test/java-excluded/` empty.
- `mvn clean install -pl jooby` passes all checks (dependency:analyze, SpotBugs, Apache RAT).
- SpotBugs exclude filter (`spotbugs-exclude.xml`) suppresses the remaining upstream findings after triage.
- Apache RAT exclusions added for resource files and the temporary migration layout.
- Removed `spotbugs-annotations` dependency (no upstream source uses `@SuppressFBWarnings`).
- Removed `websocket-jetty-server` dependency (WebSocket factory code removed from Jetty adapter).
- Added explicit deps for `websocket-jetty-api`, `jetty-io`, `jetty-util`, `javax.inject` (transitive but used directly).

## 7. Migrate EasyMock + PowerMock to Mockito ✅

- Full migration to **Mockito 5** (`mockito-core:5.3.1`, managed by `killbill-oss-parent`) is complete; EasyMock and PowerMock are removed from the active test tree.
- `MockUnit.java` was rewritten around Mockito APIs, including native static and construction mocking support.
- The temporary `src/test/java-excluded/` test set has been fully reintegrated into the active test tree.

## 8. Documentation ✅

- `jooby/README.md` created — documents upstream sources, forked modules table, git diff command, and why the fork exists.
- `jooby/CHANGES.md` created — full audit of all deviations from upstream (license headers, Java source changes, dependency version mapping, structural changes).
- All license headers updated to Kill Bill standard (single copyright line, non-javadoc comment style).
- `jooby-netty` exclusion documented in both README and CHANGES.md.

## 9. SpotBugs & Static Analysis ✅

- Ran SpotBugs on the forked source and triaged the findings instead of keeping a blanket suppression.
- Added targeted `spotbugs-exclude.xml` rules for the remaining upstream findings and wired the filter into the module build.
- Fixed one real upstream bug during triage: `Response.Forwarding.setResetHeadersOnError()` now delegates to `rsp` instead of recursing on `this`.
- Cleaned up additional CI/static-analysis fallout: removed the obsolete `Issue1087.java` / `jackson-annotations` dependency path and fixed the CodeQL ReDoS findings in `RoutePattern.java` and `PemReader.java`.

## 10. Publish as SNAPSHOT ✅

- Confirmed `mvn clean install` from the repository root succeeds in a clean worktree and installs `killbill-jooby` to the local Maven repository.
- Verified downstream consumers can depend on `org.kill-bill.commons:killbill-jooby:0.27.0-SNAPSHOT`.
- Confirmed the packaged artifact contains the expected `org.jooby.*` classes from the merged Jooby core, servlet, jetty, jackson, and funzy sources.

## 11. Restore `FileConfTest` ✅

- Source: `src/test/java-excluded/org/jooby/FileConfTest.java`
- Target path: `src/test/java/org/jooby/FileConfTest.java`
- Restored as a real filesystem-based test in `src/test/java/org/jooby/FileConfTest.java`.
- Re-review target: current `Jooby.fileConfig(String)` behavior in `src/main/java/org/jooby/Jooby.java`.

## 12. Restore `LogbackConfTest` ✅

- Source: `src/test/java-excluded/org/jooby/LogbackConfTest.java`
- Target path: `src/test/java/org/jooby/LogbackConfTest.java`
- Restored as a real filesystem/config-driven test in `src/test/java/org/jooby/LogbackConfTest.java`.
- Re-review target: current `Jooby.logback(Config)` behavior in `src/main/java/org/jooby/Jooby.java`.

## 13. Restore `RequestScopeTest` ✅

- Source: `src/test/java-excluded/org/jooby/internal/RequestScopeTest.java`
- Target path: `src/test/java/org/jooby/internal/RequestScopeTest.java`
- Restored as a direct behavior test in `src/test/java/org/jooby/internal/RequestScopeTest.java`.
- Re-review target: `src/main/java/org/jooby/internal/RequestScope.java`.

## 14. Rewrite `JettyHandlerTest` ✅

- Source: `src/test/java-excluded/org/jooby/internal/jetty/JettyHandlerTest.java`
- Target path: `src/test/java/org/jooby/internal/jetty/JettyHandlerTest.java`
- Restored as a current-behavior Jetty 10 test in `src/test/java/org/jooby/internal/jetty/JettyHandlerTest.java`.
- Re-review target: `src/main/java/org/jooby/internal/jetty/JettyHandler.java`.

## 15. Rewrite `JettyServerTest` ✅

- Source: `src/test/java-excluded/org/jooby/internal/jetty/JettyServerTest.java`
- Target path: `src/test/java/org/jooby/internal/jetty/JettyServerTest.java`
- Restored as a direct Jetty 10 wiring test in `src/test/java/org/jooby/internal/jetty/JettyServerTest.java`.
- Re-review target: `src/main/java/org/jooby/internal/jetty/JettyServer.java`.

## 16. Restore `SseFeature` ✅

- Source: `src/test/java-excluded/SseFeature.java`
- Target path: `src/test/java/org/jooby/test/SseFeature.java`
- Restored as a JDK 11 `HttpClient`-based SSE utility in `src/test/java/org/jooby/test/SseFeature.java`.
- Re-review target: current test utilities under `src/test/java/org/jooby/test/`, especially `Client.java`.

- Current final Phase 1 test state: `124` Java files in `src/test/java`, `108` runnable test classes, `923` tests, and `src/test/java-excluded/` empty.

---

# Phase 2 — JDK & Guice Foundation Upgrades

> These steps prepare the foundation for the Jakarta namespace migration.
> They do NOT change any `javax.*` imports — only upgrade the JDK and Guice versions.

## 1. Upgrade to JDK 17 (Prerequisite for Jakarta)

- Override root `project.build.targetJdk=17` in this repository so the parent-managed compiler configuration emits Java 17 bytecode.
- Fix any JDK 17 deprecation warnings or removed APIs across all modules (including `killbill-jooby`).
- Verify all dependencies are compatible with JDK 17 and remove inherited test JVM flags that are invalid on JDK 17.
- Run the full test suite under JDK 17 to establish a green baseline.
- Update CI pipelines to build and test with JDK 17.
- Current state: completed in-repo via root `project.build.targetJdk=17` override plus root `surefireArgLine` cleanup for JDK 17 test JVMs.

## 2. Upgrade to Guice 6.0 (Bridge Version)

- Guice 6.0 supports **both** `javax.inject` and `jakarta.inject` simultaneously, making it the ideal bridge.
- Override root dependency management in this repository to use `com.google.inject:guice:6.0.0`.
- Override root dependency management in this repository to use `com.google.inject.extensions:guice-servlet:6.0.0`.
- Fix any API incompatibilities in `killbill-jooby` caused by the Guice upgrade (Jooby 1.6.9 was written against Guice 4.2; Kill Bill currently uses 5.1.0).
- Run the full test suite to confirm Guice 6.0 is a drop-in replacement for 5.x.
- Note: Guice 6.0's `servlet` and `persist` extensions still use `javax.*` — this is expected and fine.
- Current state: completed in-repo; full reactor tests passed on JDK 17 with Guice 6.0.0.

---

# Phase 3 — Jakarta Namespace Migration (javax → jakarta)

> Migrate all `javax.*` imports to `jakarta.*` one namespace at a time.
> Guice 6.0 (from Phase 2) supports both namespaces, so modules can be migrated incrementally.

## 1. javax.inject → jakarta.inject

- Replace `javax.inject:javax.inject` with `jakarta.inject:jakarta.inject-api:2.0.1` in all POMs.
- In all Java source files, replace `import javax.inject.*` with `import jakarta.inject.*`.
  - Affected modules: `skeleton` (5 files), `queue` (4 files), `jdbi` (2 files), `metrics` (1 file), and `killbill-jooby` (forked Jooby source).
  - Affected annotations: `@Inject`, `@Named`, `@Singleton`, `@Provider`.
- Verify Guice 6.0 resolves `jakarta.inject` annotations correctly alongside any remaining `javax.inject` from transitive deps.
- Run the full test suite to confirm no injection failures.

## 2. javax.servlet → jakarta.servlet

- Upgrade `jakarta.servlet:jakarta.servlet-api` from `4.x` (transitional) to `6.0.0` (true Jakarta namespace).
- In all Java source files, replace `import javax.servlet.*` with `import jakarta.servlet.*`.
  - Affected modules: `skeleton` (8 files), `metrics` (4 files), and `killbill-jooby` (forked Jooby servlet/jetty source under `org.jooby.servlet`/`org.jooby.jetty` packages).
  - Affected classes: `HttpServlet`, `ServletContext`, `Filter`, `FilterChain`, `HttpServletRequest`, `HttpServletResponse`, etc.
- Update `GuiceServletContextListener` and `JULServletContextListener` to use `jakarta.servlet` equivalents.
- Note: `guice-servlet` 6.0 still uses `javax.servlet` — this creates a temporary incompatibility.
  - Workaround: keep `guice-servlet` on 6.0 and defer `skeleton`/`metrics` servlet migration until Phase 3 / Step 4 (Guice 7.0).
  - Alternative: migrate `skeleton` servlet code and `guice-servlet` together in Phase 3 / Step 4.

## 3. javax.ws.rs → jakarta.ws.rs + Jersey 3.x (skeleton only)

- Upgrade `jakarta.ws.rs:jakarta.ws.rs-api` from `2.x` (transitional) to `3.1.0` (true Jakarta namespace).
- In all Java source files, replace `import javax.ws.rs.*` with `import jakarta.ws.rs.*`.
  - Affected module: `skeleton` only (6 files).
  - Affected annotations: `@Path`, `@GET`, `@POST`, `@Produces`, `@Consumes`, `@QueryParam`, `@PathParam`, etc.
- Upgrade Jersey from **2.39.1 to 3.x** (Jersey 3.x uses `jakarta.ws.rs`).
  - Update all `org.glassfish.jersey.*` dependencies to their 3.x equivalents.
  - Update `org.glassfish.hk2:guice-bridge` and `hk2-api` to Jakarta-compatible versions.
- Run skeleton tests (`TestJerseyBaseServerModule`) to verify Jersey 3.x + Jakarta JAX-RS works.

## 4. Upgrade to Guice 7.0.0 (Final Jakarta)

- Guice 7.0.0 supports **only** `jakarta.inject`, `jakarta.servlet`, `jakarta.persistence` — no `javax.*` at all.
- This step MUST come after Phase 2 / Step 2 and the earlier Jakarta migration steps in this phase are complete.
- Update `killbill-oss-parent` to use `com.google.inject:guice:7.0.0`.
- Update `com.google.inject.extensions:guice-servlet` to `7.0.0` (now uses `jakarta.servlet`).
  - This resolves the `guice-servlet` / `jakarta.servlet` incompatibility noted above.
- Verify no transitive dependency still pulls in `javax.inject` or `javax.servlet` (use `mvn dependency:tree`).
- Run the full test suite to confirm everything works with Guice 7.0.0.

## 5. javax.xml.bind → jakarta.xml.bind (JAXB)

- Upgrade `jakarta.xml.bind:jakarta.xml.bind-api` from transitional to `4.0.0` (true Jakarta namespace).
- In all Java source files, replace `import javax.xml.bind.*` with `import jakarta.xml.bind.*`.
  - Affected modules: `automaton` (7 files) and `xmlloader` (7 files).
  - Affected classes: `JAXBContext`, `Marshaller`, `Unmarshaller`, `@XmlRootElement`, `@XmlElement`, etc.
- Update JAXB runtime implementation (e.g., `org.glassfish.jaxb:jaxb-runtime`) to 4.x.
- Run automaton and xmlloader tests to verify XML serialization/deserialization still works.
- This step is independent of Guice and can be done in parallel with the earlier Jakarta migration work.

---

# Phase 4 — Continue Modernizing the Existing killbill-jooby Fork

> With the codebase fully on Jakarta (Phase 3), keep the existing `killbill-jooby`
> fork based on upstream Jooby 1.6.9 and continue modernizing it in place.
> This is fork maintenance, not a re-fork onto a newer upstream major version.

## 1. Complete Jakarta-native Fork Maintenance

- Preserve the current vendored source base under `jooby/src/main/java/`; do not replace it with a newer upstream codebase.
- Preserve the current `org.jooby` API surface and Kill Bill module layout unless a change is required for compatibility.
- Finish any remaining `javax.*` → `jakarta.*` updates inside the forked source and tests that are still needed after Phase 3.
- Update `jooby/pom.xml` dependencies only as needed to keep the maintained fork compatible with the selected Jakarta-era baseline.
- Keep the current forked test tree and adapt it to the maintained fork behavior; do not swap it out for a different upstream major-version test tree.
- Re-verify Kill Bill-specific changes documented in `jooby/CHANGES.md` and keep them applied on top of the maintained fork.
- Update `jooby/README.md` and `jooby/CHANGES.md` to reflect the current maintained-fork baseline and any new compatibility notes.
- Run `mvn clean install` to verify compilation and tests pass.

---

# Phase 5 — JDK Target Upgrades

> With all dependencies on Jakarta and modern versions, upgrade the JDK target.

## 1. Upgrade to JDK 21

- Update `killbill-oss-parent` to set `maven.compiler.release=21`.
- Fix any JDK 21 deprecation warnings or removed APIs (e.g., `SecurityManager` removal, finalization deprecation).
- Verify all dependencies (Guice 7, Jersey 3, the maintained `killbill-jooby` fork, etc.) are compatible with JDK 21.
- Run the full test suite under JDK 21.
- Consider adopting JDK 21 features incrementally (virtual threads, pattern matching, sealed classes) in later tasks.

## 2. Upgrade to JDK 25 (Future)

- Update `maven.compiler.release=25` once JDK 25 is GA and dependencies support it.
- Address any further API removals or deprecations introduced between JDK 21 and 25.
- Verify all third-party dependencies have JDK 25-compatible releases.
- Run full test suite and integration tests under JDK 25.

---

# Cross-Cutting Concerns (All Phases)

## 1. Process & Verification Guidelines

- Each section (step) must be a **separate branch and PR** — never bundle multiple steps.
- After each step, run `mvn dependency:tree -Dverbose` to check for split-package or duplicate-namespace conflicts.
- After each step, verify downstream projects (`killbill`, `killbill-plugin-api`, etc.) still compile against the updated `killbill-commons`.
- Maintain a compatibility matrix documenting which JDK/Guice/Jakarta versions each `killbill-commons` release supports.
- Consider using OpenRewrite recipes (`org.openrewrite.java.migrate.jakarta`) to automate bulk `javax → jakarta` refactoring.
- Keep `jsr305` (`@Nullable`, `@Nonnull`) as-is — these are not part of the Jakarta EE migration.
- When migrating `killbill-skeleton`, decide whether to keep it alongside `killbill-jooby` or deprecate it.
  - If deprecated, mark with `@Deprecated` and update downstream consumers before removal.
