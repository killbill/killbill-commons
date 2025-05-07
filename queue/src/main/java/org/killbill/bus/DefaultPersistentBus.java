/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.bus;

import java.sql.Connection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;

import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.BusEventWithMetadata;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.bus.dao.PersistentBusSqlDao;
import org.killbill.bus.dispatching.BusCallableCallback;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.killbill.commons.eventbus.EventBus;
import org.killbill.commons.jdbi.notification.DatabaseTransactionNotificationApi;
import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.api.Timer;
import org.killbill.commons.metrics.impl.NoOpMetricRegistry;
import org.killbill.commons.profiling.Profiling;
import org.killbill.commons.profiling.ProfilingFeature;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.queue.DBBackedQueue;
import org.killbill.queue.DBBackedQueue.ReadyEntriesWithMetrics;
import org.killbill.queue.DBBackedQueueWithInflightQueue;
import org.killbill.queue.DBBackedQueueWithPolling;
import org.killbill.queue.DefaultQueueLifecycle;
import org.killbill.queue.InTransaction;
import org.killbill.queue.api.PersistentQueueConfig.PersistentQueueMode;
import org.killbill.queue.api.QueueEvent;
import org.killbill.queue.dao.EventEntryModelDao;
import org.killbill.queue.dispatching.BlockingRejectionExecutionHandler;
import org.killbill.queue.dispatching.Dispatcher;
import org.killbill.queue.dispatching.EventEntryDeserializer;
import org.skife.config.AugmentedConfigurationObjectFactory;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

public class DefaultPersistentBus extends DefaultQueueLifecycle implements PersistentBus {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersistentBus.class);

    private final DBI dbi;
    private final EventBus eventBusDelegate;
    private final DBBackedQueue<BusEventModelDao> dao;
    private final Clock clock;
    private final PersistentBusConfig config;
    private final Profiling<Iterable<BusEventModelDao>, RuntimeException> prof;
    private final BusReaper reaper;

    private final Dispatcher<BusEvent, BusEventModelDao> dispatcher;

    // Time it takes to handle the bus request (going through multiple handles potentially)
    private final Timer busHandlersProcessingTime;

    private final AtomicBoolean isInitialized;
    private final AtomicBoolean isStarted;
    private final String dbBackedQId;

    private final BusCallableCallback busCallableCallback;

    private static final class EventBusDelegate extends EventBus {

        public EventBusDelegate(final String busName) {
            super(busName);
        }
    }

    @Inject
    public DefaultPersistentBus(@Named(QUEUE_NAME) final IDBI dbi, final Clock clock, final PersistentBusConfig config, final MetricRegistry metricRegistry, final DatabaseTransactionNotificationApi databaseTransactionNotificationApi) {
        super(config.getTableName(), config, metricRegistry);
        this.dbi = (DBI) dbi;
        this.clock = clock;
        this.config = config;
        this.dbBackedQId = config.getTableName();
        this.dao = config.getPersistentQueueMode() == PersistentQueueMode.STICKY_EVENTS ?
                   new DBBackedQueueWithInflightQueue<>(clock, dbi, PersistentBusSqlDao.class, config, dbBackedQId, metricRegistry, databaseTransactionNotificationApi) :
                   new DBBackedQueueWithPolling<>(clock, dbi, PersistentBusSqlDao.class, config, dbBackedQId, metricRegistry);

        this.prof = new Profiling<>();
        final ThreadFactory busThreadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(new ThreadGroup(EVENT_BUS_GROUP_NAME),
                                  r,
                                  config.getTableName() + "-th");
            }
        };

        this.busHandlersProcessingTime = metricRegistry.timer(String.format("%s.%s.%s", DefaultPersistentBus.class.getName(), dbBackedQId, "busHandlersProcessingTime"));

        this.eventBusDelegate = new EventBusDelegate("Killbill EventBus");
        this.isInitialized = new AtomicBoolean(false);
        this.isStarted = new AtomicBoolean(false);
        this.reaper = new BusReaper(this.dao, config, clock);

        this.busCallableCallback = new BusCallableCallback(this);
        this.dispatcher = new Dispatcher<>(1,
                                           config,
                                           10,
                                           TimeUnit.MINUTES,
                                           config.getShutdownTimeout().getPeriod(),
                                           config.getShutdownTimeout().getUnit(),
                                           new LinkedBlockingQueue<>(config.getEventQueueCapacity()),
                                           busThreadFactory,
                                           new BlockingRejectionExecutionHandler(),
                                           clock,
                                           busCallableCallback,
                                           this);

    }

    public DefaultPersistentBus(final DataSource dataSource, final Properties properties) {
        this(InTransaction.buildDDBI(dataSource),
             new DefaultClock(),
             new AugmentedConfigurationObjectFactory(properties).buildWithReplacements(PersistentBusConfig.class, Map.of("instanceName", "main")),
             new NoOpMetricRegistry(),
             new DatabaseTransactionNotificationApi());
    }

    @Override
    public boolean initQueue() {
        if (config.isProcessingOff()) {
            log.warn("PersistentBus processing is off, cannot be initialized");
            return false;
        }

        if (isInitialized.compareAndSet(false, true)) {
            dao.initialize();
            dispatcher.start();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean startQueue() {

        if (config.isProcessingOff()) {
            log.warn("PersistentBus processing is off, cannot be started");
            return false;
        }

        if (!isInitialized.get()) {
            // Make it easy for our tests, so they simply call startQueue
            initQueue();
        }

        if (isStarted.compareAndSet(false, true)) {
            reaper.start();
            super.startQueue();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean stopQueue() {
        if (!isStarted.compareAndSet(true, false)) {
            return true;
        }

        log.info("Shutting down bus");

        isInitialized.set(false);
        boolean terminated = true;

        // Stop the reaper first
        if (!reaper.stop()) {
            terminated = false;
        }
        // Then, the lifecycle dispatcher threads (no new work accepted)
        if (!super.stopLifecycleDispatcher()) {
            terminated = false;
        }
        // Then, stop the working threads (finish on-going work)
        if (!dispatcher.stopDispatcher()) {
            terminated = false;
        }
        // Finally, stop the completion threads (cleanup recently finished work)
        if (!super.stopLifecycleCompletion()) {
            terminated = false;
        }

        dao.close();

        return terminated;
    }

    @Override
    public DispatchResultMetrics doDispatchEvents() {
        final ReadyEntriesWithMetrics<BusEventModelDao> eventsWithMetrics = dao.getReadyEntries();
        final List<BusEventModelDao> events = eventsWithMetrics.getEntries();
        if (events.isEmpty()) {
            return new DispatchResultMetrics(0, eventsWithMetrics.getTime());
        }
        log.debug("Bus events from {} to process: {}", config.getTableName(), events);

        long ini = System.nanoTime();
        for (final BusEventModelDao cur : events) {
            dispatcher.dispatch(cur);
        }
        return new DispatchResultMetrics(events.size(), (System.nanoTime() - ini) + eventsWithMetrics.getTime());
    }

    @Override
    public void doProcessCompletedEvents(final Iterable<? extends EventEntryModelDao> completed) {
        busCallableCallback.moveCompletedOrFailedEvents((Iterable<BusEventModelDao>) completed);
    }

    @Override
    public void doProcessRetriedEvents(final Iterable<? extends EventEntryModelDao> retried) {
        Iterator<? extends EventEntryModelDao> it = retried.iterator();
        while (it.hasNext()) {
            BusEventModelDao cur = (BusEventModelDao) it.next();
            busCallableCallback.updateRetriedEvents(cur);
        }
    }

    @Override
    public boolean isStarted() {
        return isStarted.get();
    }

    @Override
    public void register(final Object handlerInstance) throws EventBusException {
        if (isInitialized.get()) {
            eventBusDelegate.register(handlerInstance);
        } else {
            log.warn("Attempting to register handler " + handlerInstance + " in a non initialized bus");
        }
    }

    @Override
    public void unregister(final Object handlerInstance) throws EventBusException {
        if (isInitialized.get()) {
            eventBusDelegate.unregister(handlerInstance);
        } else {
            log.warn("Attempting to unregister handler " + handlerInstance + " in a non initialized bus");
        }
    }

    @Override
    public void post(final BusEvent event) throws EventBusException {
        try {
            if (isInitialized.get()) {
                final String json = objectWriter.writeValueAsString(event);
                final BusEventModelDao entry = new BusEventModelDao(CreatorName.get(), clock.getUTCNow(), event.getClass().getName(), json,
                                                                    event.getUserToken(), event.getSearchKey1(), event.getSearchKey2());
                dao.insertEntry(entry);

            } else {
                log.warn("Attempting to post event " + event + " in a non initialized bus");
            }
        } catch (final Exception e) {
            log.error("Failed to post BusEvent " + event, e);
        }
    }

    @Override
    public void postFromTransaction(final BusEvent event, final Connection connection) throws EventBusException {
        if (!isInitialized.get()) {
            log.warn("Attempting to post event " + event + " in a non initialized bus");
            return;
        }

        final String json;
        try {
            json = objectWriter.writeValueAsString(event);
        } catch (final JsonProcessingException e) {
            log.warn("Unable to serialize event " + event, e);
            return;
        }

        final BusEventModelDao entry = new BusEventModelDao(CreatorName.get(),
                                                            clock.getUTCNow(),
                                                            event.getClass().getName(),
                                                            json,
                                                            event.getUserToken(),
                                                            event.getSearchKey1(),
                                                            event.getSearchKey2());

        final InTransaction.InTransactionHandler<PersistentBusSqlDao, Void> handler = new InTransaction.InTransactionHandler<PersistentBusSqlDao, Void>() {

            @Override
            public Void withSqlDao(final PersistentBusSqlDao transactional) {
                dao.insertEntryFromTransaction(transactional, entry);
                return null;
            }
        };

        InTransaction.execute(dbi, connection, handler, PersistentBusSqlDao.class);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableBusEventsForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return getAvailableBusEventsForSearchKeysInternal((PersistentBusSqlDao) dao.getSqlDao(), null, searchKey1, searchKey2);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableBusEventsFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<PersistentBusSqlDao, Iterable<BusEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<PersistentBusSqlDao, Iterable<BusEventWithMetadata<T>>>() {
            @Override
            public Iterable<BusEventWithMetadata<T>> withSqlDao(final PersistentBusSqlDao transactional) {
                return getAvailableBusEventsForSearchKeysInternal(transactional, null, searchKey1, searchKey2);
            }
        };
        return InTransaction.execute(dbi, connection, handler, PersistentBusSqlDao.class);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableBusEventsForSearchKey2(final DateTime maxCreatedDate, final Long searchKey2) {
        return getAvailableBusEventsForSearchKeysInternal((PersistentBusSqlDao) dao.getSqlDao(), maxCreatedDate, null, searchKey2);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableBusEventsFromTransactionForSearchKey2(final DateTime maxCreatedDate, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<PersistentBusSqlDao, Iterable<BusEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<PersistentBusSqlDao, Iterable<BusEventWithMetadata<T>>>() {
            @Override
            public Iterable<BusEventWithMetadata<T>> withSqlDao(final PersistentBusSqlDao transactional) {
                return getAvailableBusEventsForSearchKeysInternal(transactional, maxCreatedDate, null, searchKey2);
            }
        };
        return InTransaction.execute(dbi, connection, handler, PersistentBusSqlDao.class);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getInProcessingBusEvents() {
        return toBusEventWithMetadata(dao.getSqlDao().getInProcessingEntries(config.getTableName()));
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return getAvailableOrInProcessingBusEventsForSearchKeysInternal((PersistentBusSqlDao) dao.getSqlDao(), null, searchKey1, searchKey2);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<PersistentBusSqlDao, Iterable<BusEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<PersistentBusSqlDao, Iterable<BusEventWithMetadata<T>>>() {
            @Override
            public Iterable<BusEventWithMetadata<T>> withSqlDao(final PersistentBusSqlDao transactional) {
                return getAvailableOrInProcessingBusEventsForSearchKeysInternal(transactional, null, searchKey1, searchKey2);
            }
        };
        return InTransaction.execute(dbi, connection, handler, PersistentBusSqlDao.class);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsForSearchKey2(final DateTime maxCreatedDate, final Long searchKey2) {
        return getAvailableOrInProcessingBusEventsForSearchKeysInternal((PersistentBusSqlDao) dao.getSqlDao(), maxCreatedDate, null, searchKey2);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsFromTransactionForSearchKey2(final DateTime maxCreatedDate, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<PersistentBusSqlDao, Iterable<BusEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<PersistentBusSqlDao, Iterable<BusEventWithMetadata<T>>>() {
            @Override
            public Iterable<BusEventWithMetadata<T>> withSqlDao(final PersistentBusSqlDao transactional) {
                return getAvailableOrInProcessingBusEventsForSearchKeysInternal(transactional, maxCreatedDate, null, searchKey2);
            }
        };
        return InTransaction.execute(dbi, connection, handler, PersistentBusSqlDao.class);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getHistoricalBusEventsForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return getHistoricalBusEventsForSearchKeysInternal((PersistentBusSqlDao) dao.getSqlDao(), null, searchKey1, searchKey2);
    }

    @Override
    public <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getHistoricalBusEventsForSearchKey2(final DateTime minCreatedDate, final Long searchKey2) {
        return getHistoricalBusEventsForSearchKeysInternal((PersistentBusSqlDao) dao.getSqlDao(), minCreatedDate, null, searchKey2);
    }

    @Override
    public long getNbReadyEntries(final DateTime maxCreatedDate) {
        return dao.getNbReadyEntries(maxCreatedDate.toDate());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultPersistentBus{");
        sb.append("dbBackedQId='").append(dbBackedQId).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public void dispatchBusEventWithMetrics(final QueueEvent event) throws org.killbill.commons.eventbus.EventBusException {
        final long ini = System.nanoTime();
        try {
            eventBusDelegate.postWithException(event);
        } finally {
            busHandlersProcessingTime.update(System.nanoTime() - ini, TimeUnit.NANOSECONDS);
        }
    }

    private <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableBusEventsForSearchKeysInternal(final PersistentBusSqlDao transactionalDao, @Nullable final DateTime maxCreatedDate, @Nullable final Long searchKey1, final Long searchKey2) {
        final Iterable<BusEventModelDao> entries = getReadyQueueEntriesForSearchKeysWithProfiling(transactionalDao, maxCreatedDate, searchKey1, searchKey2);
        return toBusEventWithMetadata(entries);
    }

    private <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsForSearchKeysInternal(final PersistentBusSqlDao transactionalDao, @Nullable final DateTime maxCreatedDate, @Nullable final Long searchKey1, final Long searchKey2) {
        final Iterable<BusEventModelDao> entries = getReadyOrInProcessingQueueEntriesForSearchKeysWithProfiling(transactionalDao, maxCreatedDate, searchKey1, searchKey2);
        return toBusEventWithMetadata(entries);
    }

    private <T extends BusEvent> Iterable<BusEventWithMetadata<T>> getHistoricalBusEventsForSearchKeysInternal(final PersistentBusSqlDao transactionalDao, @Nullable final DateTime minCreatedDate, @Nullable final Long searchKey1, final Long searchKey2) {
        final Iterable<BusEventModelDao> entries = getHistoricalQueueEntriesForSearchKeysWithProfiling(transactionalDao, minCreatedDate, searchKey1, searchKey2);
        return toBusEventWithMetadata(entries);
    }

    private Iterable<BusEventModelDao> getReadyQueueEntriesForSearchKeysWithProfiling(final PersistentBusSqlDao transactionalDao, @Nullable final DateTime maxCreatedDate, @Nullable final Long searchKey1, final Long searchKey2) {
        return prof.executeWithProfiling(ProfilingFeature.ProfilingFeatureType.DAO, "DAO:PersistentBusSqlDao:getReadyQueueEntriesForSearchKeys", new Profiling.WithProfilingCallback<Iterable<BusEventModelDao>, RuntimeException>() {
            @Override
            public Iterable<BusEventModelDao> execute() throws RuntimeException {
                return new Iterable<BusEventModelDao>() {
                    @Override
                    public Iterator<BusEventModelDao> iterator() {
                        return searchKey1 != null ?
                               transactionalDao.getReadyQueueEntriesForSearchKeys(searchKey1, searchKey2, config.getTableName()) :
                               transactionalDao.getReadyQueueEntriesForSearchKey2(maxCreatedDate, searchKey2, config.getTableName());
                    }
                };
            }
        });
    }

    private Iterable<BusEventModelDao> getReadyOrInProcessingQueueEntriesForSearchKeysWithProfiling(final PersistentBusSqlDao transactionalDao, @Nullable final DateTime maxCreatedDate, @Nullable final Long searchKey1, final Long searchKey2) {
        return prof.executeWithProfiling(ProfilingFeature.ProfilingFeatureType.DAO, "DAO:PersistentBusSqlDao:getReadyOrInProcessingQueueEntriesForSearchKeys", new Profiling.WithProfilingCallback<Iterable<BusEventModelDao>, RuntimeException>() {
            @Override
            public Iterable<BusEventModelDao> execute() throws RuntimeException {
                return new Iterable<BusEventModelDao>() {
                    @Override
                    public Iterator<BusEventModelDao> iterator() {
                        return searchKey1 != null ?
                               transactionalDao.getReadyOrInProcessingQueueEntriesForSearchKeys(searchKey1, searchKey2, config.getTableName()) :
                               transactionalDao.getReadyOrInProcessingQueueEntriesForSearchKey2(maxCreatedDate, searchKey2, config.getTableName());
                    }
                };
            }
        });
    }

    private Iterable<BusEventModelDao> getHistoricalQueueEntriesForSearchKeysWithProfiling(final PersistentBusSqlDao transactionalDao, @Nullable final DateTime minCreatedDate, @Nullable final Long searchKey1, final Long searchKey2) {
        return prof.executeWithProfiling(ProfilingFeature.ProfilingFeatureType.DAO, "DAO:PersistentBusSqlDao:getHistoricalQueueEntriesForSearchKeys", new Profiling.WithProfilingCallback<Iterable<BusEventModelDao>, RuntimeException>() {
            @Override
            public Iterable<BusEventModelDao> execute() throws RuntimeException {
                return new Iterable<BusEventModelDao>() {
                    @Override
                    public Iterator<BusEventModelDao> iterator() {
                        return searchKey1 != null ?
                               transactionalDao.getHistoricalQueueEntriesForSearchKeys(searchKey1, searchKey2, config.getHistoryTableName()) :
                               transactionalDao.getHistoricalQueueEntriesForSearchKey2(minCreatedDate, searchKey2, config.getHistoryTableName());
                    }
                };
            }
        });
    }

    private <T extends BusEvent> Iterable<BusEventWithMetadata<T>> toBusEventWithMetadata(final Iterable<BusEventModelDao> entries) {
        return Iterables.toStream(entries)
                .map(entry -> {
                    final T event = EventEntryDeserializer.deserialize(entry, objectReader);
                    return new BusEventWithMetadata<T>(entry.getRecordId(),
                                                       entry.getUserToken(),
                                                       entry.getCreatedDate(),
                                                       entry.getSearchKey1(),
                                                       entry.getSearchKey2(),
                                                       event);
                })
                .collect(Collectors.toUnmodifiableList());
    }

    public DBBackedQueue<BusEventModelDao> getDao() {
        return dao;
    }

    public Clock getClock() {
        return clock;
    }

    public PersistentBusConfig getConfig() {
        return config;
    }
}
