# killbill-commons
![Maven Central](https://img.shields.io/maven-central/v/org.kill-bill.commons/killbill-commons?color=blue&label=Maven%20Central)

Kill Bill reusable Java components:

* **automaton**: framework to build state machines
* **clock**: clock library
* **concurrent**: extensions to java.util.concurrent.Executors
* **config-magic**: fork of [config-magic](https://github.com/brianm/config-magic)
* **embeddeddb**: library to embed databases
* **jdbi**: fork of [jDBI](https://github.com/jdbi/jdbi)
* **locker**: locking library
* **metrics**: annotation-based metrics
* **queue**: provides persistent bus events and notifications
* **skeleton**: framework to build web services
* **xmlloader**: library to load, parse and validate XML files

## Kill Bill compatibility

| Commons version | Kill Bill version |
| --------------: | ----------------: |
| 0.15.y          | 0.16.z            |
| 0.20.y          | 0.18.z            |
| 0.21.y          | 0.19.z            |
| 0.23.y          | 0.20.z            |
| 0.23.y          | 0.22.z            |
| 0.24.y          | 0.22.z            |

We've upgraded numerous dependencies in 0.24.x (required for Java 11 support).

## Usage

Add the relevant submodule(s) to a project:

```xml
<dependency>
    <groupId>org.kill-bill.commons</groupId>
    <artifactId>killbill-automaton</artifactId>
    <version>... release version ...</version>
</dependency>
<dependency>
    <groupId>org.kill-bill.commons</groupId>
    <artifactId>killbill-clock</artifactId>
    <version>... release version ...</version>
</dependency>
<dependency>
    <groupId>org.kill-bill.commons</groupId>
    <artifactId>killbill-concurrent</artifactId>
    <version>... release version ...</version>
</dependency>
<dependency>
    <groupId>org.kill-bill.commons</groupId>
    <artifactId>killbill-config-magic</artifactId>
    <version>... release version ...</version>
</dependency>
<dependency>
    <groupId>org.kill-bill.commons</groupId>
    <artifactId>killbill-embeddeddb-common</artifactId>
    <version>... release version ...</version>
</dependency>
<dependency>
    <groupId>org.kill-bill.commons</groupId>
    <artifactId>killbill-embeddeddb-h2</artifactId>
    <version>... release version ...</version>
</dependency>
<dependency>
    <groupId>org.kill-bill.commons</groupId>
    <artifactId>killbill-embeddeddb-mysql</artifactId>
    <version>... release version ...</version>
</dependency>
<dependency>
    <groupId>org.kill-bill.commons</groupId>
    <artifactId>killbill-embeddeddb-postgresql</artifactId>
    <version>... release version ...</version>
</dependency>
<dependency>
    <groupId>org.kill-bill.commons</groupId>
    <artifactId>killbill-jdbi</artifactId>
    <version>... release version ...</version>
</dependency>
<dependency>
    <groupId>org.kill-bill.commons</groupId>
    <artifactId>killbill-locker</artifactId>
    <version>... release version ...</version>
</dependency>
<dependency>
    <groupId>org.kill-bill.commons</groupId>
    <artifactId>killbill-metrics</artifactId>
    <version>... release version ...</version>
</dependency>
<dependency>
    <groupId>org.kill-bill.commons</groupId>
    <artifactId>killbill-queue</artifactId>
    <version>... release version ...</version>
</dependency>
<dependency>
    <groupId>org.kill-bill.commons</groupId>
    <artifactId>killbill-skeleton</artifactId>
    <version>... release version ...</version>
</dependency>
```

## About

Kill Bill is the leading Open-Source Subscription Billing & Payments Platform. For more information about the project, go to https://killbill.io/.
