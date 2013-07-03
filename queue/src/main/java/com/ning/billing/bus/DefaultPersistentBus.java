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

import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.Hostname;
import com.ning.billing.bus.api.BusEventBase;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.bus.api.PersistentBusConfig;
import com.ning.billing.bus.dao.BusEventEntry;
import com.ning.billing.bus.dao.PersistentBusSqlDao;
import com.ning.billing.queue.DBBackedQueue;
import com.ning.billing.queue.DefaultQueueLifecycle;
import com.ning.billing.queue.api.EventEntry.PersistentQueueEntryLifecycleState;
import com.ning.billing.util.clock.Clock;

import com.google.common.eventbus.EventBus;

public class DefaultPersistentBus extends DefaultQueueLifecycle implements PersistentBus {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersistentBus.class);
    private final EventBusDelegate eventBusDelegate;
    private final DBBackedQueue<BusEventEntry> dao;
    private final Clock clock;

    private volatile boolean isStarted;

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
        this.dao = new DBBackedQueue<BusEventEntry>(clock, sqlDao, config, busTableName, busHistoryTableName,  "bus-" + busTableName, true);
        this.eventBusDelegate = new EventBusDelegate("Killbill EventBus");
        this.isStarted = false;
    }

    @Override
    public void start() {
        dao.initialize();
        startQueue();
        isStarted = true;
    }

    @Override
    public void stop() {
        stopQueue();
        isStarted = false;
    }

    @Override
    public int doProcessEvents() {
        final List<BusEventEntry> events = dao.getReadyEntries();
        if (events.size() == 0) {
            return 0;
        }

        int result = 0;
        for (final BusEventEntry cur : events) {
            final String jsonWithAccountAndTenantRecorId = tweakJsonToIncludeSearchKeys(cur.getEventJson(), cur.getSearchKey1(), cur.getSearchKey2());
            final BusEventBase evt = deserializeEvent(cur.getClassName(), objectMapper, jsonWithAccountAndTenantRecorId);
            result++;
            // STEPH exception handling is done by GUAVA-- logged a bug Issue-780
            eventBusDelegate.post(evt);
            BusEventEntry processedEntry = new BusEventEntry(cur, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
            dao.moveEntryToHistory(processedEntry);
        }
        return result;
    }


    @Override
    public boolean isStarted() {
        return isStarted;
    }

    @Override
    public void register(final Object handlerInstance) throws EventBusException {
        if (isStarted) {
            eventBusDelegate.register(handlerInstance);
        } else {
            log.warn("Attempting to register handler " + handlerInstance + " in a non initialized bus");
        }
    }

    @Override
    public void unregister(final Object handlerInstance) throws EventBusException {
        if (isStarted) {
            eventBusDelegate.unregister(handlerInstance);
        } else {
            log.warn("Attempting to unregister handler " + handlerInstance + " in a non initialized bus");
        }
    }

    @Override
    public void post(final BusEventBase event) throws EventBusException {
        try {
            if (isStarted) {
                final String json = objectMapper.writeValueAsString(event);
                final BusEventEntry entry = new BusEventEntry(Hostname.get(), clock.getUTCNow(), event.getClass().getName(), json, event.getUserToken(), event.getSearchKey1(), event.getSearchKey2());
                dao.insertEntry(entry);

            } else {
                log.warn("Attempting to post event " + event + " in a non initialized bus");
            }
        } catch (Exception e) {
            log.error("Failed to post BusEventBase " + event, e);
        }
    }

    @Override
    public void postFromTransaction(final BusEventBase event, final Transmogrifier transmogrifier)
            throws EventBusException {
        try {
            final PersistentBusSqlDao transactional = transmogrifier.become(PersistentBusSqlDao.class);
            if (isStarted) {
                final String json = objectMapper.writeValueAsString(event);
                final BusEventEntry entry = new BusEventEntry(Hostname.get(), clock.getUTCNow(), event.getClass().getName(), json, event.getUserToken(), event.getSearchKey1(), event.getSearchKey2());
                dao.insertEntryFromTransaction(transactional, entry);
            } else {
                log.warn("Attempting to post event " + event + " in a non initialized bus");
            }
        } catch (Exception e) {
            log.error("Failed to post BusEventBase " + event, e);
        }
    }


    // TODO Fix searchKey -> accountRecordId,...
    private String tweakJsonToIncludeSearchKeys(final String input, final Long searchKey1, final Long searchKey2) {
        final int lastIndexPriorFinalBracket = input.lastIndexOf("}");
        final StringBuilder tmp = new StringBuilder(input.substring(0, lastIndexPriorFinalBracket));
        //tmp.append(",\"accountRecordId\":");
        tmp.append(",\"searchKey1\":");
        tmp.append(searchKey1);
        //tmp.append(",\"tenantRecordId\":");
        tmp.append(",\"searchKey2\":");
        tmp.append(searchKey2);
        tmp.append("}");
        return tmp.toString();
    }
}
