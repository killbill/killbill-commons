/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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
import com.google.common.eventbus.EventBusThatThrowsException;
import org.killbill.Hostname;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.bus.dao.PersistentBusSqlDao;
import org.killbill.clock.Clock;
import org.killbill.commons.jdbi.notification.DatabaseTransactionNotificationApi;
import org.killbill.commons.jdbi.transaction.NotificationTransactionHandler;
import org.killbill.queue.DBBackedQueue;
import org.killbill.queue.DefaultQueueLifecycle;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultPersistentBus extends DefaultQueueLifecycle implements PersistentBus {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersistentBus.class);
    private final EventBusDelegate eventBusDelegate;
    private final DBBackedQueue<BusEventModelDao> dao;
    private final Clock clock;

    private AtomicBoolean isStarted;

    private static final class EventBusDelegate extends EventBusThatThrowsException {

        public EventBusDelegate(final String busName) {
            super(busName);
        }
    }

    @Inject
    public DefaultPersistentBus(@Named(QUEUE_NAME) final IDBI dbi, final Clock clock, final PersistentBusConfig config, final MetricRegistry metricRegistry, final DatabaseTransactionNotificationApi databaseTransactionNotificationApi) {
        super("Bus", Executors.newFixedThreadPool(config.getNbThreads(), new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(new ThreadGroup(EVENT_BUS_GROUP_NAME),
                        r,
                        EVENT_BUS_TH_NAME);
            }
        }), config.getNbThreads(), config);
        final PersistentBusSqlDao sqlDao = dbi.onDemand(PersistentBusSqlDao.class);
        this.clock = clock;
        this.dao = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, "bus-" + config.getTableName(), metricRegistry, databaseTransactionNotificationApi);
        this.eventBusDelegate = new EventBusDelegate("Killbill EventBus");
        this.isStarted = new AtomicBoolean(false);
    }

    @Override
    public void start() {
        if (isStarted.compareAndSet(false, true)) {
            dao.initialize();
            startQueue();
        }
    }

    @Override
    public void stop() {
        if (isStarted.compareAndSet(true, false)) {
            stopQueue();
        }
    }

    @Override
    public int doProcessEvents() {
        final List<BusEventModelDao> events = dao.getReadyEntries();
        if (events.size() == 0) {
            return 0;
        }

        int result = 0;
        final List<BusEventModelDao> historyEvents = new ArrayList<BusEventModelDao>();
        for (final BusEventModelDao cur : events) {
            final BusEvent evt = deserializeEvent(cur.getClassName(), objectMapper, cur.getEventJson());
            result++;

            long errorCount = cur.getErrorCount();
            Throwable lastException = null;
            try {
                eventBusDelegate.postWithException(evt);
            } catch (com.google.common.eventbus.EventBusException e) {

                if (e.getCause() != null && e.getCause() instanceof InvocationTargetException) {
                    lastException = e.getCause().getCause();
                } else {
                    lastException = e;
                }
                errorCount++;
            } finally {
                if (lastException == null) {
                    BusEventModelDao processedEntry = new BusEventModelDao(cur, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
                    historyEvents.add(processedEntry);
                } else if (errorCount <= config.getMaxFailureRetries()) {
                    log.info("Bus dispatch error, will attempt a retry ", lastException);
                    // STEPH we could batch those as well
                    BusEventModelDao retriedEntry = new BusEventModelDao(cur, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.AVAILABLE, errorCount);
                    dao.updateOnError(retriedEntry);
                } else {
                    log.error("Fatal Bus dispatch error, data corruption...", lastException);
                    BusEventModelDao processedEntry = new BusEventModelDao(cur, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.FAILED);
                    historyEvents.add(processedEntry);
                }
            }
        }
        dao.moveEntriesToHistory(historyEvents);
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
                final BusEventModelDao entry = new BusEventModelDao(Hostname.get(), clock.getUTCNow(), event.getClass().getName(), json,
                        event.getUserToken(), event.getSearchKey1(), event.getSearchKey2());
                dao.insertEntry(entry);

            } else {
                log.warn("Attempting to post event " + event + " in a non initialized bus");
            }
        } catch (Exception e) {
            log.error("Failed to post BusEvent " + event, e);
        }
    }

    @Override
    public void postFromTransaction(final BusEvent event, final Transmogrifier transmogrifier)
            throws EventBusException {
        try {
            final PersistentBusSqlDao transactional = transmogrifier.become(PersistentBusSqlDao.class);
            if (isStarted.get()) {
                final String json = objectMapper.writeValueAsString(event);
                final BusEventModelDao entry = new BusEventModelDao(Hostname.get(), clock.getUTCNow(), event.getClass().getName(), json,
                        event.getUserToken(), event.getSearchKey1(), event.getSearchKey2());
                dao.insertEntryFromTransaction(transactional, entry);
            } else {
                log.warn("Attempting to post event " + event + " in a non initialized bus");
            }
        } catch (Exception e) {
            log.error("Failed to post BusEvent " + event, e);
        }
    }
}
