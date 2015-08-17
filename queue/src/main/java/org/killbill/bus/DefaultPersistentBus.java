/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBusThatThrowsException;
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
import org.killbill.commons.jdbi.notification.DatabaseTransactionNotificationApi;
import org.killbill.queue.DBBackedQueue;
import org.killbill.queue.DefaultQueueLifecycle;
import org.killbill.queue.InTransaction;
import org.killbill.queue.api.QueueEvent;
import org.killbill.queue.dispatching.CallableCallbackBase;
import org.killbill.queue.dispatching.Dispatcher;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultPersistentBus extends DefaultQueueLifecycle implements PersistentBus {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersistentBus.class);
    private final EventBusThatThrowsException eventBusDelegate;
    private final DBBackedQueue<BusEventModelDao> dao;
    private final Clock clock;
    private final PersistentBusConfig config;
    private final Timer dispatchTimer;

    private final Dispatcher<BusEventModelDao> dispatcher;
    private AtomicBoolean isStarted;

    private static final class EventBusDelegate extends EventBusThatThrowsException {
        public EventBusDelegate(final String busName) {
            super(busName);
        }
    }

    @Inject
    public DefaultPersistentBus(@Named(QUEUE_NAME) final IDBI dbi, final Clock clock, final PersistentBusConfig config, final MetricRegistry metricRegistry, final DatabaseTransactionNotificationApi databaseTransactionNotificationApi) {
        super("Bus", config);
        final PersistentBusSqlDao sqlDao = dbi.onDemand(PersistentBusSqlDao.class);
        this.clock = clock;
        this.config = config;
        final String dbBackedQId = "bus-" + config.getTableName();
        this.dao = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, dbBackedQId, metricRegistry, databaseTransactionNotificationApi);

        final ThreadFactory busThreadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(new ThreadGroup(EVENT_BUS_GROUP_NAME),
                        r,
                        config.getTableName() + "-th");
            }
        };
        final RejectedExecutionHandler rejectedExecutionHandler = new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {

            }
        };

        this.dispatchTimer = metricRegistry.timer(MetricRegistry.name(DefaultPersistentBus.class, "dispatch"));
        this.dispatcher = new Dispatcher(1, config.geMaxDispatchThreads(), 10, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>(/* fixed bounds, threads,...*/), busThreadFactory, rejectedExecutionHandler);

        this.eventBusDelegate = new EventBusDelegate("Killbill EventBus");
        this.isStarted = new AtomicBoolean(false);
    }

    public DefaultPersistentBus(final DataSource dataSource, final Properties properties) {
        this(InTransaction.buildDDBI(dataSource), new DefaultClock(), new ConfigurationObjectFactory(properties).buildWithReplacements(PersistentBusConfig.class, ImmutableMap.<String, String>of("instanceName", "main")),
                new MetricRegistry(), new DatabaseTransactionNotificationApi());
    }

    @Override
    public void start() {
        if (isStarted.compareAndSet(false, true)) {
            dao.initialize();
            startQueue();
            dispatcher.start();
        }
    }

    @Override
    public void stop() {
        if (isStarted.compareAndSet(true, false)) {
            stopQueue();
            dispatcher.stop();
        }
    }

    @Override
    public int doProcessEvents() {
        final List<BusEventModelDao> events = dao.getReadyEntries();
        if (events.size() == 0) {
            return 0;
        }

        int result = 0;
        for (final BusEventModelDao cur : events) {
            final BusCallableCallback callback = new BusCallableCallback(this);
            dispatcher.dispatch(cur, callback);
            result++;
        }
        return result;
    }

    @Override
    public boolean isStarted() {
        return isStarted.get();
    }

    @Override
    public void register(final Object handlerInstance) throws EventBusException {
        if (isStarted.get()) {
            eventBusDelegate.register(handlerInstance);
        } else {
            log.warn("Attempting to register handler " + handlerInstance + " in a non initialized bus");
        }
    }

    @Override
    public void unregister(final Object handlerInstance) throws EventBusException {
        if (isStarted.get()) {
            eventBusDelegate.unregister(handlerInstance);
        } else {
            log.warn("Attempting to unregister handler " + handlerInstance + " in a non initialized bus");
        }
    }

    @Override
    public void post(final BusEvent event) throws EventBusException {
        try {
            if (isStarted.get()) {
                final String json = objectMapper.writeValueAsString(event);
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
        if (!isStarted.get()) {
            log.warn("Attempting to post event " + event + " in a non initialized bus");
            return;
        }

        final String json;
        try {
            json = objectMapper.writeValueAsString(event);
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
            public Void withSqlDao(final PersistentBusSqlDao transactional) throws Exception {
                dao.insertEntryFromTransaction(transactional, entry);
                return null;
            }
        };

        InTransaction.execute(connection, handler, PersistentBusSqlDao.class);
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableBusEventsForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return getAvailableBusEventsForSearchKeysInternal((PersistentBusSqlDao) dao.getSqlDao(), searchKey1, searchKey2);
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableBusEventsFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<PersistentBusSqlDao, List<BusEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<PersistentBusSqlDao, List<BusEventWithMetadata<T>>>() {
            @Override
            public List<BusEventWithMetadata<T>> withSqlDao(final PersistentBusSqlDao transactional) throws Exception {
                return getAvailableBusEventsForSearchKeysInternal(transactional, searchKey1, searchKey2);
            }
        };
        return InTransaction.execute(connection, handler, PersistentBusSqlDao.class);
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableBusEventsForSearchKey2(final Long searchKey2) {
        return getAvailableBusEventsForSearchKeysInternal((PersistentBusSqlDao) dao.getSqlDao(), null, searchKey2);
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableBusEventsFromTransactionForSearchKey2(final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<PersistentBusSqlDao, List<BusEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<PersistentBusSqlDao, List<BusEventWithMetadata<T>>>() {
            @Override
            public List<BusEventWithMetadata<T>> withSqlDao(final PersistentBusSqlDao transactional) throws Exception {
                return getAvailableBusEventsForSearchKeysInternal(transactional, null, searchKey2);
            }
        };
        return InTransaction.execute(connection, handler, PersistentBusSqlDao.class);
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getInProcessingBusEvents() {
        return toBusEventWithMetadataList(dao.getSqlDao().getInProcessingEntries(config.getTableName()));
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsForSearchKeys(final Long searchKey1, final Long searchKey2) {
        return getAvailableOrInProcessingBusEventsForSearchKeysInternal((PersistentBusSqlDao) dao.getSqlDao(), searchKey1, searchKey2);
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsFromTransactionForSearchKeys(final Long searchKey1, final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<PersistentBusSqlDao, List<BusEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<PersistentBusSqlDao, List<BusEventWithMetadata<T>>>() {
            @Override
            public List<BusEventWithMetadata<T>> withSqlDao(final PersistentBusSqlDao transactional) throws Exception {
                return getAvailableOrInProcessingBusEventsForSearchKeysInternal(transactional, searchKey1, searchKey2);
            }
        };
        return InTransaction.execute(connection, handler, PersistentBusSqlDao.class);
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsForSearchKey2(final Long searchKey2) {
        return getAvailableOrInProcessingBusEventsForSearchKeysInternal((PersistentBusSqlDao) dao.getSqlDao(), null, searchKey2);
    }

    @Override
    public <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsFromTransactionForSearchKey2(final Long searchKey2, final Connection connection) {
        final InTransaction.InTransactionHandler<PersistentBusSqlDao, List<BusEventWithMetadata<T>>> handler = new InTransaction.InTransactionHandler<PersistentBusSqlDao, List<BusEventWithMetadata<T>>>() {
            @Override
            public List<BusEventWithMetadata<T>> withSqlDao(final PersistentBusSqlDao transactional) throws Exception {
                return getAvailableOrInProcessingBusEventsForSearchKeysInternal(transactional, null, searchKey2);
            }
        };
        return InTransaction.execute(connection, handler, PersistentBusSqlDao.class);
    }

    public void dispatchBusEventWithMetrics(final QueueEvent event) throws com.google.common.eventbus.EventBusException {
        final Timer.Context dispatchTimerContext = dispatchTimer.time();
        try {
            eventBusDelegate.postWithException(event);
        } finally {
            dispatchTimerContext.stop();
        }
    }

    private <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableBusEventsForSearchKeysInternal(final PersistentBusSqlDao transactionalDao, @Nullable final Long searchKey1, final Long searchKey2) {
        final List<BusEventModelDao> entries = searchKey1 != null ?
                transactionalDao.getReadyQueueEntriesForSearchKeys(searchKey1, searchKey2, config.getTableName()) :
                transactionalDao.getReadyQueueEntriesForSearchKey2(searchKey2, config.getTableName());
        return toBusEventWithMetadataList(entries);
    }

    private <T extends BusEvent> List<BusEventWithMetadata<T>> getAvailableOrInProcessingBusEventsForSearchKeysInternal(final PersistentBusSqlDao transactionalDao, @Nullable final Long searchKey1, final Long searchKey2) {
        final List<BusEventModelDao> entries = searchKey1 != null ?
                transactionalDao.getReadyOrInProcessingQueueEntriesForSearchKeys(searchKey1, searchKey2, config.getTableName()) :
                transactionalDao.getReadyOrInProcessingQueueEntriesForSearchKey2(searchKey2, config.getTableName());
        return toBusEventWithMetadataList(entries);
    }

    private <T extends BusEvent> List<BusEventWithMetadata<T>> toBusEventWithMetadataList(final List<BusEventModelDao> entries) {
        final List<BusEventWithMetadata<T>> result = new LinkedList<BusEventWithMetadata<T>>();
        for (final BusEventModelDao entry : entries) {
            final T event = (T) CallableCallbackBase.deserializeEvent(entry, objectMapper);
            final BusEventWithMetadata<T> eventWithMetadata = new BusEventWithMetadata<T>(entry.getRecordId(),
                    entry.getUserToken(),
                    entry.getCreatedDate(),
                    entry.getSearchKey1(),
                    entry.getSearchKey2(),
                    event);
            result.add(eventWithMetadata);
        }
        return result;
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
