/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.notificationq;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.ning.billing.Hostname;
import com.ning.billing.clock.Clock;
import com.ning.billing.notificationq.api.NotificationEvent;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueConfig;
import com.ning.billing.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.notificationq.dao.NotificationEventModelDao;
import com.ning.billing.notificationq.dao.NotificationSqlDao;
import com.ning.billing.queue.DBBackedQueue;
import com.ning.billing.queue.DefaultQueueLifecycle;
import com.ning.billing.queue.api.PersistentQueueEntryLifecycleState;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class NotificationQueueDispatcher extends DefaultQueueLifecycle {

    protected static final Logger log = LoggerFactory.getLogger(NotificationQueueDispatcher.class);

    public static final int CLAIM_TIME_MS = (5 * 60 * 1000); // 5 minutes

    private static final String NOTIFICATION_THREAD_NAME = "Notification-queue-dispatch";

    private final AtomicLong nbProcessedEvents;

    protected final NotificationQueueConfig config;
    protected final Clock clock;
    protected final Map<String, NotificationQueue> queues;
    protected final DBBackedQueue<NotificationEventModelDao> dao;
    protected final MetricRegistry metricRegistry;

    //
    // Metrics
    //
    private final Gauge pendingNotifications;
    private final Counter processedNotificationsSinceStart;
    private final Map<String, Histogram> perQueueProcessingTime;

    // Package visibility on purpose
    NotificationQueueDispatcher(final Clock clock, final NotificationQueueConfig config, final IDBI dbi, final MetricRegistry metricRegistry) {
        super("NotificationQ", Executors.newFixedThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                final Thread th = new Thread(r);
                th.setName(NOTIFICATION_THREAD_NAME);
                th.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(final Thread t, final Throwable e) {
                        log.error("Uncaught exception for thread " + t.getName(), e);
                    }
                });
                return th;
            }
        }), config.getNbThreads(), config);

        this.clock = clock;
        this.config = config;
        this.nbProcessedEvents = new AtomicLong();
        final NotificationSqlDao sqlDao = (dbi != null) ? dbi.onDemand(NotificationSqlDao.class) : null;
        this.dao = new DBBackedQueue<NotificationEventModelDao>(clock, sqlDao, config, "notif-" + config.getTableName(), metricRegistry);

        this.queues = new TreeMap<String, NotificationQueue>();

        this.metricRegistry = metricRegistry;
        this.pendingNotifications = metricRegistry.register(MetricRegistry.name(NotificationQueueDispatcher.class, "pending-notifications"),
                new Gauge<Integer>() {
                    @Override
                    public Integer getValue() {
                        // TODO STEPH
                        return 0; // STEPH dao != null ? dao.getPendingCountNotifications(clock.getUTCNow().toDate()) : 0;
                    }
                });

        this.processedNotificationsSinceStart = metricRegistry.counter(MetricRegistry.name(NotificationQueueDispatcher.class, "processed-notifications-since-start"));
        this.perQueueProcessingTime = new HashMap<String, Histogram>();
    }

    @Override
    public void stopQueue() {
        if (config.isProcessingOff() || !isStarted()) {
            return;
        }

        // If there are no active queues left, stop the processing for the queues
        // (This is not intended to be robust against a system that would stop and start queues at the same time,
        // for a a normal shutdown sequence)
        //
        int nbQueueStarted = 0;
        synchronized (queues) {
            for (final NotificationQueue cur : queues.values()) {
                if (cur.isStarted()) {
                    nbQueueStarted++;
                }
            }
        }
        if (nbQueueStarted == 0) {
            super.stopQueue();
        }
    }

    public AtomicLong getNbProcessedEvents() {
        return nbProcessedEvents;
    }

    public Clock getClock() {
        return clock;
    }


    protected NotificationQueueHandler getHandlerForActiveQueue(final String compositeName) {
        synchronized (queues) {
            final NotificationQueue queue = queues.get(compositeName);
            if (queue == null || !queue.isStarted()) {
                return null;
            }
            return queue.getHandler();
        }
    }

    @Override
    public int doProcessEvents() {
        return doProcessEventsWithLimit(-1);
    }

    protected int doProcessEventsWithLimit(int limit) {

        logDebug("ENTER doProcessEvents");
        final List<NotificationEventModelDao> notifications = getReadyNotifications();
        if (notifications.size() == 0) {
            logDebug("EXIT doProcessEvents");
            return 0;
        }
        logDebug("doProcessEventsWithLimit date = %s, got %s", getClock().getUTCNow().toDate(), notifications.size());

        if (limit > 0) {
            while (notifications.size() > limit) {
                notifications.remove(notifications.size() - 1);
            }
        }

        logDebug("START processing %d events at time %s", notifications.size(), getClock().getUTCNow().toDate());

        int result = 0;
        for (final NotificationEventModelDao cur : notifications) {
            getNbProcessedEvents().incrementAndGet();
            final NotificationEvent key = deserializeEvent(cur.getClassName(), objectMapper, cur.getEventJson());

            NotificationQueueHandler handler = getHandlerForActiveQueue(cur.getQueueName());
            if (handler == null) {
                continue;
            }

            NotificationQueueException lastException = null;
            long errorCount = cur.getErrorCount();
            try {
                handleNotificationWithMetrics(handler, cur, key);
            } catch (NotificationQueueException e) {
                lastException = e;
                errorCount++;
            } finally {
                if (lastException == null) {
                    result++;
                    clearNotification(cur);
                    logDebug("done handling notification %s, key = %s for time %s", cur.getRecordId(), cur.getEventJson(), cur.getEffectiveDate());
                } else if (errorCount <= config.getMaxFailureRetries()) {
                    log.info("NotificationQ dispatch error, will attempt a retry ", lastException);
                    NotificationEventModelDao failedNotification = new NotificationEventModelDao(cur, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.AVAILABLE, errorCount);
                    dao.updateOnError(failedNotification);
                } else {
                    log.error("Fatal NotificationQ dispatch error, data corruption...", lastException);
                    clearFailedNotification(cur);
                }
            }
        }
        return result;
    }

    private void handleNotificationWithMetrics(final NotificationQueueHandler handler, final NotificationEventModelDao notification, final NotificationEvent key) throws NotificationQueueException {

        // Create specific metric name because:
        // - ':' is not allowed for metric name
        // - name would be too long (e.g entitlement-service:subscription-events-process-time -> ent-subscription-events-process-time)
        //
        final String[] parts = notification.getQueueName().split(":");
        final String metricName = new StringBuilder(parts[0].substring(0, 3))
                .append("-")
                .append(parts[1])
                .append("-process-time").toString();

        final Histogram perQueueHistogramProcessingTime;
        synchronized (perQueueProcessingTime) {
            if (!perQueueProcessingTime.containsKey(notification.getQueueName())) {
                perQueueProcessingTime.put(notification.getQueueName(), metricRegistry.histogram(MetricRegistry.name(NotificationQueueDispatcher.class, metricName)));
            }
            perQueueHistogramProcessingTime = perQueueProcessingTime.get(notification.getQueueName());
        }
        final DateTime beforeProcessing = clock.getUTCNow();

        try {
            handler.handleReadyNotification(key, notification.getEffectiveDate(), notification.getFutureUserToken(), notification.getSearchKey1(), notification.getSearchKey2());
        } catch (RuntimeException e) {
            throw new NotificationQueueException(e);
        } finally {
            // Unclear if those stats should include failures
            final DateTime afterProcessing = clock.getUTCNow();
            perQueueHistogramProcessingTime.update(afterProcessing.getMillis() - beforeProcessing.getMillis());
            processedNotificationsSinceStart.inc();
        }
    }

    private void clearNotification(final NotificationEventModelDao cleared) {
        NotificationEventModelDao processedEntry = new NotificationEventModelDao(cleared, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
        dao.moveEntryToHistory(processedEntry);
    }

    private void clearFailedNotification(final NotificationEventModelDao cleared) {
        NotificationEventModelDao processedEntry = new NotificationEventModelDao(cleared, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.FAILED);
        dao.moveEntryToHistory(processedEntry);
    }

    private List<NotificationEventModelDao> getReadyNotifications() {

        final List<NotificationEventModelDao> input = dao.getReadyEntries();
        final List<NotificationEventModelDao> claimedNotifications = new ArrayList<NotificationEventModelDao>();
        for (final NotificationEventModelDao cur : input) {

            // Skip non active queues...
            final NotificationQueue queue = queues.get(cur.getQueueName());
            if (queue == null || !queue.isStarted()) {
                continue;
            }
            claimedNotifications.add(cur);
        }
        return claimedNotifications;
    }

    private void logDebug(final String format, final Object... args) {
        if (log.isDebugEnabled()) {
            final String realDebug = String.format(format, args);
            log.debug(String.format("Thread %d  %s", Thread.currentThread().getId(), realDebug));
        }
    }


    public static String getCompositeName(final String svcName, final String queueName) {
        return svcName + ":" + queueName;
    }
}
