/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.clock.Clock;
import org.killbill.commons.concurrent.Executors;
import org.killbill.queue.DBBackedQueue;
import org.killbill.queue.api.Reaper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BusReaper implements Reaper {

    private final DBBackedQueue<BusEventModelDao> dao;
    private final PersistentBusConfig config;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final AtomicBoolean isStarted;
    private ScheduledFuture<?> reapEntriesHandle;

    private static final Logger log = LoggerFactory.getLogger(BusReaper.class);

    public BusReaper(final DBBackedQueue<BusEventModelDao> dao, final PersistentBusConfig config, final Clock clock) {
        this.dao = dao;
        this.config = config;
        this.clock = clock;
        this.isStarted = new AtomicBoolean(false);
        this.scheduler = Executors.newSingleThreadScheduledExecutor("ReaperExecutor");
    }

    @Override
    public void start() {
        if (!isStarted.compareAndSet(false, true)) {
            return;
        }

        final long pendingPeriod;

        // if Claim time is greater than reap threshold
        if (config.getClaimedTime().getMillis() >= config.getReapThreshold().getMillis()) {
            // override reap threshold using claim time + 5 minutes
            pendingPeriod = config.getClaimedTime().getMillis() + 300000;
            log.warn(String.format("Reap threshold was mis-configured. Claim time [%s] is greater than reap threshold [%s]",
                                   config.getClaimedTime().toString(), config.getReapThreshold().toString()));

        } else {
            pendingPeriod = config.getReapThreshold().getMillis();
        }

        final Runnable reapEntries = new Runnable() {
            @Override
            public void run() {
                dao.updateCreatingOwner(getReapingDate());
            }

            private Date getReapingDate() {
                return clock.getUTCNow().minusMillis((int) pendingPeriod).toDate();
            }
        };

        reapEntriesHandle = scheduler.scheduleWithFixedDelay(reapEntries, pendingPeriod, pendingPeriod, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        if (!isStarted.compareAndSet(true, false)) {
            return;
        }

        if (!reapEntriesHandle.isCancelled() || !reapEntriesHandle.isDone()) {
            reapEntriesHandle.cancel(true);
        }

        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Reaper stop sequence has been interrupted");
            }
        }
    }

    @Override
    public boolean isStarted() {
        return isStarted.get();
    }

}
