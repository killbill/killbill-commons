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
    private ScheduledFuture<?> reapEntriesHandle;

    private static final Logger log = LoggerFactory.getLogger(BusReaper.class);

    public BusReaper(final DBBackedQueue<BusEventModelDao> dao, final PersistentBusConfig config, final Clock clock) {
        this.dao = dao;
        this.config = config;
        this.clock = clock;
        this.scheduler = Executors.newSingleThreadScheduledExecutor("ReaperExecutor");
    }

    @Override
    public void start() {
        final TimeUnit pendingRateUnit = config.getReapThreshold().getUnit();
        final long pendingPeriod = config.getReapThreshold().getPeriod();

        final Runnable reapEntries = new Runnable() {
            @Override
            public void run() {
                dao.updateCreatingOwner(getReapingDate());
            }

            private Date getReapingDate() {
                final TimeUnit rateUnit = config.getReapThreshold().getUnit();
                final long threshold = config.getReapThreshold().getPeriod();

                if ( rateUnit == TimeUnit.SECONDS) {
                    return clock.getUTCNow().minusSeconds((int) threshold).toDate();
                } else if ( rateUnit == TimeUnit.MINUTES) {
                    return clock.getUTCNow().minusMinutes((int) threshold).toDate();
                } else if ( rateUnit == TimeUnit.HOURS) {
                    return clock.getUTCNow().minusHours((int) threshold).toDate();
                } else if ( rateUnit == TimeUnit.DAYS) {
                    return clock.getUTCNow().minusDays((int) threshold).toDate();
                } else {
                    return clock.getUTCNow().minusMillis((int) threshold).toDate();
                }
            }
        };

        reapEntriesHandle = scheduler.scheduleAtFixedRate(reapEntries, pendingPeriod, pendingPeriod, pendingRateUnit);
    }

    @Override
    public void stop() {
        if (!reapEntriesHandle.isCancelled() || !reapEntriesHandle.isDone()) {
            reapEntriesHandle.cancel(true);
        }

        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                log.info(String.format("Reaper stop sequence has been interrupted"));
            }
        }
    }

}
