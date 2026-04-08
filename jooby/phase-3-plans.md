# Phase 3 Plans

This document records the current Jooby fork baseline and Phase 3 constraints while the repository
moves from `javax.*` to `jakarta.*`.

## Current Baseline

- Repository baseline validated on Temurin **JDK 17.0.18** with **Maven 3.8.5**
- Root dependency baseline:
  - `project.build.targetJdk=17`
  - `com.google.inject:guice=6.0.0`
  - `com.google.inject.extensions:guice-servlet=6.0.0`
- Root build baseline:
  - `mvn -q clean install -DskipTests`
- Root test baseline:
  - `mvn clean test`
- Jooby module test baseline:
  - `mvn clean test`
- Active Jooby test tree:
  - `124` Java files in `src/test/java`
  - `108` runnable test classes
  - `923` tests
  - `src/test/java-excluded/` is empty

## Current Dependency Baseline Relevant to Phase 3

| Area | Current state |
|---|---|
| Inject namespace | `killbill-jooby`, `queue`, `jdbi`, and `metrics` now use `jakarta.inject`; `skeleton` remains on `javax.inject` until Jersey 2 / HK2 is replaced |
| Servlet namespace | `jakarta.servlet:jakarta.servlet-api:4.0.4` is still a transitional artifact that exposes `javax.servlet` packages |
| JAX-RS namespace | `skeleton` still uses `javax.ws.rs` with Jersey 2.39.1 |
| Guice servlet bridge | `guice-servlet` is still `6.0.0`, so servlet integration remains on the `javax.servlet` side |
| Jetty | The Jooby fork already runs on Jetty `10.0.16` |

## Phase 3 Sequence

1. `javax.inject` -> `jakarta.inject` for modules not blocked by Jersey 2 / HK2
2. `javax.servlet` -> `jakarta.servlet`
3. `javax.ws.rs` -> `jakarta.ws.rs` plus Jersey 3
4. Upgrade to Guice 7 once the earlier Jakarta steps are ready

## Phase 3 Notes

- `jooby/CHANGES.md` is the durable source of truth for fork differences from upstream Jooby 1.6.9.
- The Jooby module is still the best canary because it exercises Guice, servlet, Jetty, and the
  migrated Mockito-based test tree in one module.
- The old `-Pjooby` test gate is gone; Jooby tests run in the standard Maven lifecycle.
- JDK 17 does not need to be the machine default, but Maven commands for this phase must run in a
  shell that has JDK 17 selected explicitly when multiple JDKs are installed.
- Root `surefireArgLine` already overrides the parent's obsolete JDK 17 profile flags.
- Full reactor tests, plus focused `jooby`, `skeleton`, `metrics`, and `queue` test runs, already
  passed with JDK 17 + Guice 6.0.0.
- `guice-servlet` 6.0.0 still uses `javax.servlet`; that makes Phase 3 servlet work awkward until
  the Guice 7 step is taken or coordinated with it.
- Guice 6 accepts `jakarta.inject` annotations, but provider-facing Jooby code still needs
  `com.google.inject.Provider` when interacting with Guice binding APIs.
- `skeleton` must stay on `javax.inject` until the Jersey 2 / HK2 layer is migrated because HK2
  still enforces `javax.inject.Singleton` semantics during Jersey bootstrap.
