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

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionIsolationLevel;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.Hostname;
import com.ning.billing.bus.dao.BusEventEntry;
import com.ning.billing.bus.dao.PersistentBusSqlDao;
import com.ning.billing.queue.DefaultQueueLifecycle;
import com.ning.billing.util.clock.Clock;

import com.google.common.eventbus.EventBus;

public class DefaultPersistentBus extends DefaultQueueLifecycle implements PersistentBus {

    private static final long DELTA_IN_PROCESSING_TIME_MS = 1000L * 60L * 5L; // 5 minutes

    private static final Logger log = LoggerFactory.getLogger(DefaultPersistentBus.class);

    private final static int QUEUE_CAPACITY = 3000;

    private final PersistentBusSqlDao dao;

    private final EventBusDelegate eventBusDelegate;
    private final Clock clock;

    private volatile boolean isStarted;

    private final String tableName;

    private final Queue inflightEvents;
    private final AtomicBoolean isOverflowedToDisk;

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
    public DefaultPersistentBus(final IDBI dbi, final Clock clock, final PersistentBusConfig config, final String busTableName) {
        super("Bus", Executors.newFixedThreadPool(config.getNbThreads(), new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(new ThreadGroup(EVENT_BUS_GROUP_NAME),
                                  r,
                                  EVENT_BUS_TH_NAME);
            }
        }), config.getNbThreads(), config);
        this.dao = dbi.onDemand(PersistentBusSqlDao.class);
        this.clock = clock;
        this.eventBusDelegate = new EventBusDelegate("Killbill EventBus");
        this.tableName = busTableName;
        this.inflightEvents = new LinkedBlockingQueue(QUEUE_CAPACITY);
        this.isOverflowedToDisk = new AtomicBoolean(false);
        this.isStarted = false;
    }

    @Override
    public void start() {
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
        final List<BusEventEntry> events = getNextBusEvent();
        if (events.size() == 0) {
            return 0;
        }

        int result = 0;
        for (final BusEventEntry cur : events) {
            final String jsonWithAccountAndTenantRecorId = tweakJsonToIncludeAccountAndTenantRecordId(cur.getBusEventJson(), cur.getAccountRecordId(), cur.getTenantRecordId());
            final BusPersistentEvent evt = deserializeEvent(cur.getBusEventClass(), objectMapper, jsonWithAccountAndTenantRecorId);
            result++;
            // STEPH exception handling is done by GUAVA-- logged a bug Issue-780
            eventBusDelegate.post(evt);
            dao.clearBusEvent(cur.getId(), Hostname.get(), tableName);
        }
        return result;
    }


    @Override
    public boolean isStarted() {
        return isStarted;
    }

    private List<BusEventEntry> getNextBusEvent() {
        return getNextBusEventFromDB();
    }

    private List<BusEventEntry> getNextBusEventFromInFlightQ() {
        return null;

    }

    private List<BusEventEntry> getNextBusEventFromDB() {
        final Date now = clock.getUTCNow().toDate();
        final Date nextAvailable = clock.getUTCNow().plus(DELTA_IN_PROCESSING_TIME_MS).toDate();

        final List<BusEventEntry> entries = dao.getNextBusEventEntries(config.getPrefetchAmount(), Hostname.get(), now, tableName);
        final List<BusEventEntry> claimedEntries = new LinkedList<BusEventEntry>();
        for (final BusEventEntry entry : entries) {
            final boolean claimed = (dao.claimBusEvent(Hostname.get(), nextAvailable, entry.getId(), now, tableName) == 1);
            if (claimed) {
                dao.insertClaimedHistory(Hostname.get(), now, entry.getId(), entry.getAccountRecordId(), entry.getTenantRecordId());
                claimedEntries.add(entry);
            }
        }
        return claimedEntries;
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
    public void post(final BusPersistentEvent event) throws EventBusException {
        if (isStarted) {
            dao.inTransaction(TransactionIsolationLevel.READ_COMMITTED, new Transaction<Void, PersistentBusSqlDao>() {
                @Override
                public Void inTransaction(final PersistentBusSqlDao transactional,
                                          final TransactionStatus status) throws Exception {
                    postFromTransaction(event, transactional);
                    return null;
                }
            });
        } else {
            log.warn("Attempting to post event " + event + " in a non initialized bus");
        }
    }

    @Override
    public void postFromTransaction(final BusPersistentEvent event, final Transmogrifier transmogrifier)
            throws EventBusException {
        final PersistentBusSqlDao transactional = transmogrifier.become(PersistentBusSqlDao.class);
        if (isStarted) {
            postFromTransaction(event, transactional);
        } else {
            log.warn("Attempting to post event " + event + " in a non initialized bus");
        }
    }

    private void postFromTransaction(final BusPersistentEvent event, final PersistentBusSqlDao transactional) {
        try {
            final String json = objectMapper.writeValueAsString(event);
            final BusEventEntry entry = new BusEventEntry(Hostname.get(), event.getClass().getName(), json, event.getUserToken(), event.getAccountRecordId(), event.getTenantRecordId());
            transactional.insertBusEvent(entry, tableName);
        } catch (Exception e) {
            log.error("Failed to post BusEvent " + event, e);
        }
    }

    private String tweakJsonToIncludeAccountAndTenantRecordId(final String input, final Long accountRecordId, final Long tenantRecordId) {
        final int lastIndexPriorFinalBracket = input.lastIndexOf("}");
        final StringBuilder tmp = new StringBuilder(input.substring(0, lastIndexPriorFinalBracket));
        tmp.append(",\"accountRecordId\":");
        tmp.append(accountRecordId);
        tmp.append(",\"tenantRecordId\":");
        tmp.append(tenantRecordId);
        tmp.append("}");
        return tmp.toString();
    }
}
