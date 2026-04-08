killbill-jooby
==============

Contains a fork of [Jooby 1.6.9](https://github.com/jooby-project/jooby/tree/v1.6.9) vendored for Kill Bill.
This fork is maintained in place as Kill Bill modernizes its dependencies and Jakarta compatibility.

The following upstream modules are merged into this single artifact:

| Upstream module | Package | Description |
|---|---|---|
| `org.jooby:jooby` | `org.jooby` | Core framework (routes, lifecycle, SPI) |
| `org.jooby:jooby-servlet` | `org.jooby.servlet` | Servlet API bridge |
| `org.jooby:jooby-jetty` | `org.jooby.jetty` | Jetty server runtime |
| `org.jooby:jooby-jackson` | `org.jooby.json` | Jackson JSON serialization |
| `org.jooby:funzy` | `org.jooby.funzy` | Functional utilities (Throwing, Try, When) — commit `728d743ca348f6f12430ec8735057cf6a1687c0c` from [jooby-project/funzy](https://github.com/jooby-project/funzy) |

Not forked:
- `org.jooby:jooby-netty` — Kill Bill uses Jetty; SSE/WebSocket are handled via the core SPI layer (`org.jooby.spi.*`).

## Building & Testing

`killbill-jooby` keeps the upstream **JUnit 4** test stack. It does **not** use the
repository's usual TestNG-based test setup.

Standard build:
```
mvn clean install
```

Run tests (108 test classes, 923 tests):
```
mvn clean test
```

Changes with upstream:

```
git diff -w 85a50d5e894d14068b2e90a0601481cf52a0abec...HEAD jooby/src/main/java/org/jooby
```
