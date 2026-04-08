# Phase 2 Plans

This document records the current Jooby fork baseline that Phase 2 work should preserve while
upgrading the repository foundation.

## Current Baseline

- Root build baseline validated on Temurin **JDK 11.0.26** with **Maven 3.8.5**
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
| JDK target | Repository baseline is still JDK 11 |
| Guice | `com.google.inject:guice` is `5.1.0` via `killbill-oss-parent` |
| Guice servlet bridge | `com.google.inject.extensions:guice-servlet` is still on the current parent-managed line |
| Inject namespace | Source still uses `javax.inject`; `javax.inject:javax.inject` is explicitly declared in the Jooby fork |
| Servlet namespace | `jakarta.servlet:jakarta.servlet-api:4.0.4` is used as a transitional artifact and still exposes `javax.servlet` packages |
| Jetty | Jetty already sits on `10.0.16` in the fork |

## Phase 2 Sequence

1. Upgrade the build baseline to JDK 17 without changing `javax.*` source imports
2. Re-establish a green build/test baseline on JDK 17
3. Upgrade Guice and guice-servlet to 6.0.0
4. Re-establish a green build/test baseline before starting any Jakarta namespace migration

## Notes

- `jooby/CHANGES.md` now focuses on durable source, dependency, and test-tree changes rather than
  phased execution history.
- The Jooby module is a useful canary for Phase 2 because it already exercises Guice, servlet, Jetty,
  and a large migrated Mockito-based test tree.
- The old `-Pjooby` test gate was removed after the active test tree was fully restored.
