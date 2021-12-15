killbill-queue
==============

* Notification Queue: persistent job queue, used for jobs scheduling
* Persistent Bus: persistent message bus

The persistent store is a database. The library has successfully been tested on MySQL flavors (MySQL, MariaDB, Aurora and Percona),
PostgreSQL and H2.

## Use cases

### Notification Queue

* Retries: if a call to a third-party service fails for instance, schedule a retry in the future (e.g. wait 15').
* Periodic scheduler: such as scheduling daily jobs.

### Persistent Bus

* Communicate asynchronously on a given node (STICKY) behavior
* Communicate asynchronously across nodes without an external messaging system (e.g. ActiveMQ).

## Advantages

* **Performance:** assuming a no-op processing handler, the library can sustain on a single node a rate of 150 events/s
with an introduced latency tp99 of 1ms (time between the event is inserted in the queue and the time it is processed).
* **Battle tested:** the library has successfully processed billions of entries over the years and it is expected to be
highly resilient towards all kinds of failure scenarii (e.g. nodes going down).
* **Simple:** embeddable with no third-party service dependency (e.g. Redis).

### Dependencies

For historical reasons, the library depends on a few libraries (the list could be reduced at some point if needed):

* Jackson: events are serialized as JSON in the database
* Google Guava utilities
* Dropwizard metrics: the library records various metrics for performance monitoring
* Joda Time: the library doesn't expect Java 1.8+
* Antlr: to generate SQL statements
* SLF4J
* Config-magic: as the configuration mechanism
* killbill-clock: a clock abstraction, especially useful in tests to be able to move the time as seen by the library
* killbill-concurrent: small utility wrappers around Java Executors
* killbill-jdbi: a fork of jDBI v2

Regarding jDBI, we had found odd behaviors and performance drags in the upstream code, introduced mostly because of generic features
it supports, that the queue library doesn't need. After discussing our options with the jDBI community, it was easiest to fork the code
and make the optimizations on our end, tailored to our library.

This is purely internal though and doesn't affect your application, which could still rely on upstream jDBI v2 or v3 by using
the shaded version of the library:

```
 <dependency>
     <groupId>org.kill-bill.commons</groupId>
     <artifactId>killbill-queue</artifactId>
     <classifier>jdbi-shaded</classifier>
     <exclusions>
         <exclusion>
             <groupId>org.kill-bill.commons</groupId>
             <artifactId>killbill-jdbi</artifactId>
         </exclusion>
     </exclusions>
 </dependency>
 ```


## Implementation

Both Notification Queue and Bus implementations share a great deal of code and work in a similar way: a message is persisted in the database for
immediate processing (message bus use-case) or future processing (Notification Queue use-case). The bus implementation
has some added features mostly for performance purposes.

Queues rely on two different tables (`bus_events/bus_events_history` and `notifications/notifications_history` by default):
incoming entries are inserted in the main table and moved to the history version after processing. The history tables are
purely for auditing and debugging. They can safely be truncated if need be.

### Fetching entries

Entries to be processed are fetched according to the `PersistentQueueMode` (see `getPersistentQueueMode` in the config).

For the Notification Queue, this should always be `STICKY_POLLING`: the library will poll the database for available
entries and dispatch them to the same node that created the entry.

For the Bus, this could be `STICKY_POLLING` or `STICKY_EVENTS` (default, not described here).

The polling mode works as follows: a single lifecycle thread periodically (every 3s by default, see `getPollingSleepTimeMs`
in the config) **claims** ready entries from database and dispatches them to a thread pool for processing. The processing thread
eventually notifies that main lifecycle thread of entries (successfully or not) processed which then updates the database
(batch query).

The claiming mechanism is lock free: a first query looks for entries to be processed (10 at a time by default, see `getMaxEntriesClaimed` in the config)
then mark them as `IN_PROCESSING`. Because each node only looks at entries it created (`creating_owner` column), there is
no conflict between several nodes processing the same entry.

### Reaper mechanism

Specifically for cloud environments, where nodes can come and go at any time (e.g. auto-scaling), a reaper mechanism has
been implemented: a background thread periodically scans the main table for late entries (up to 10 at a a time by default,
see `getMaxReDispatchCount` in the config) and automatically redispatches them to another node (after 10m by default, see
`getReapThreshold` in the config). The reaped entries are marked as `REAPED` and moved to the history table, while identical
entries are re-inserted in the main table for processing (`AVAILABLE` state).

### Exceptions handling

In case of an exception by the queue handler, the queue will attempt an immediate retry (up to 3 times by default,
see `getMaxFailureRetries` in the config): `processing_state` is updated back to `AVAILABLE` and `error_count` incremented.

After the number of retries is exhausted, the `processing_state` is updated to the final state `FAILED` and the entry is moved to the history table.

Because of these retries, it is important that the queue handlers are idempotent.

### State transitions

![State transitions](doc/queue_states.png?raw=true "State transitions")
