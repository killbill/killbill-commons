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

package com.ning.billing.bus;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.Hostname;
import com.ning.billing.bus.api.BusEvent;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.bus.api.PersistentBusConfig;
import com.ning.billing.bus.dao.BusEventModelDao;
import com.ning.billing.bus.dao.PersistentBusSqlDao;
import com.ning.billing.queue.DBBackedQueue;
import com.ning.billing.queue.DefaultQueueLifecycle;
import com.ning.billing.queue.api.PersistentQueueEntryLifecycleState;
import com.ning.billing.clock.Clock;

import com.google.common.eventbus.EventBus;

public class DefaultPersistentBus extends DefaultQueueLifecycle implements PersistentBus {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersistentBus.class);
    private final EventBusDelegate eventBusDelegate;
    private final DBBackedQueue<BusEventModelDao> dao;
    private final Clock clock;

    private AtomicBoolean isStarted;

    private static final class EventBusDelegate extends EventBus {

        public EventBusDelegate(final String busName) {
            super(busName);
        }

        // STEPH we can't override the method because EventHandler is package private scope
        // Logged a bug against guava (Issue 981)
        /*
        @Override
        protected void dispatch(Object event, EventHandler wrapper) {
            try {
              wrapper.handleEvent(event);
            } catch (InvocationTargetException e) {
              logger.log(Level.SEVERE,
                  "Could not dispatch event: " + event + " to handler " + wrapper, e);
            }
          }
          */
    }

    @Inject
    public DefaultPersistentBus(final IDBI dbi, final Clock clock, final PersistentBusConfig config, final String busTableName, final String busHistoryTableName) {
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
        this.dao = new DBBackedQueue<BusEventModelDao>(clock, sqlDao, config, busTableName, busHistoryTableName, "bus-" + busTableName, true);
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
        for (final BusEventModelDao cur : events) {
            final BusEvent evt = deserializeEvent(cur.getClassName(), objectMapper, cur.getEventJson());
            result++;
            eventBusDelegate.post(evt);
            BusEventModelDao processedEntry = new BusEventModelDao(cur, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
            dao.moveEntryToHistory(processedEntry);
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
