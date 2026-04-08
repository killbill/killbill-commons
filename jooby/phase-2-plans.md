# Phase 2 Plans

This document records the current Jooby fork baseline that Phase 2 work should preserve while
upgrading the repository foundation.

## Current Baseline

- Root build baseline validated on Temurin **JDK 17.0.18** with **Maven 3.8.5**
- Root build command used for baseline:
  - `mvn -q clean install -DskipTests`
- Jooby module test baseline:
  - `mvn clean test`
- Active Jooby test tree:
  - `124` Java files in `src/test/java`
  - `108` runnable test classes
  - `923` tests
  - `src/test/java-excluded/` is empty

## Current Dependency Baseline Relevant to Phase 2

| Area | Current state |
|---|---|
| JDK target | Repository baseline is now JDK 17 via root `project.build.targetJdk` override |
| Guice | `com.google.inject:guice` is now pinned to `6.0.0` via root dependency management override |
| Guice servlet bridge | `com.google.inject.extensions:guice-servlet` is now pinned to `6.0.0` via root dependency management override |
| Inject namespace | Source still uses `javax.inject`; `javax.inject:javax.inject` is explicitly declared in the Jooby fork |
| Servlet namespace | `jakarta.servlet:jakarta.servlet-api:4.0.4` is used as a transitional artifact and still exposes `javax.servlet` packages |
| Jetty | Jetty already sits on `10.0.16` in the fork |

## Phase 2 Sequence

Phase 2 foundation work is complete. The next step is Phase 3 Jakarta namespace migration.

## Notes

- `jooby/CHANGES.md` now focuses on durable source, dependency, and test-tree changes rather than
  phased execution history.
- The Jooby module is a useful canary for Phase 2 because it already exercises Guice, servlet, Jetty,
  and a large migrated Mockito-based test tree.
- The old `-Pjooby` test gate was removed after the active test tree was fully restored.
- JDK 17 does not need to be the machine default, but Phase 2 validation must run Maven in a shell
  that has JDK 17 selected explicitly when multiple JDKs are installed.
- Root `surefireArgLine` now overrides the parent's JDK 17 profile so test JVMs no longer use
  `--illegal-access=permit` or the invalid `--add-opens java.base/java.base=ALL-UNNAMED` flag.
- Full reactor tests, plus focused `jooby`, `skeleton`, `metrics`, and `queue` test runs, passed
  with JDK 17 + Guice 6.0.0.
