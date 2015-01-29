/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.notificationq;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.DateTime;
import org.killbill.Hostname;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueConfig;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.killbill.notificationq.dao.NotificationEventModelDao;
import org.killbill.notificationq.dao.NotificationSqlDao;
import org.killbill.queue.DBBackedQueue;
import org.killbill.queue.DefaultQueueLifecycle;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NotificationQueueDispatcher extends DefaultQueueLifecycle {

    protected static final Logger log = LoggerFactory.getLogger(NotificationQueueDispatcher.class);

    public static final int CLAIM_TIME_MS = (5 * 60 * 1000); // 5 minutes

    private final AtomicLong nbProcessedEvents;

    protected final NotificationQueueConfig config;
    protected final Clock clock;
    protected final Map<String, NotificationQueue> queues;
    protected final DBBackedQueue<NotificationEventModelDao> dao;
    protected final MetricRegistry metricRegistry;

    private final LinkedBlockingQueue<NotificationEventModelDao> pendingNotificationsQ;

    //
    // Metrics
    //
    private final Gauge pendingNotifications;
    private final Counter processedNotificationsSinceStart;
    private final Map<String, Histogram> perQueueProcessingTime;

    private final NotificationRunner[] runners;

    // Package visibility on purpose
    NotificationQueueDispatcher(final Clock clock, final NotificationQueueConfig config, final IDBI dbi, final MetricRegistry metricRegistry) {
        super("NotificationQ", Executors.newFixedThreadPool(config.getNbThreads() + 1, new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                final Thread th = new Thread(r);
                th.setName(config.getTableName() + "-th");
                th.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(final Thread t, final Throwable e) {
                        log.error("Uncaught exception for thread " + t.getName(), e);
                    }
                });
                return th;
            }
        }), 1, config);

        this.clock = clock;
        this.config = config;
        this.nbProcessedEvents = new AtomicLong();
        final NotificationSqlDao sqlDao = (dbi != null) ? dbi.onDemand(NotificationSqlDao.class) : null;
        this.dao = new DBBackedQueue<NotificationEventModelDao>(clock, sqlDao, config, "notif-" + config.getTableName(), metricRegistry, null);

        this.queues = new TreeMap<String, NotificationQueue>();

        this.processedNotificationsSinceStart = metricRegistry.counter(MetricRegistry.name(NotificationQueueDispatcher.class, "processed-notifications-since-start"));
        this.perQueueProcessingTime = new HashMap<String, Histogram>();
        this.pendingNotificationsQ = new LinkedBlockingQueue<NotificationEventModelDao>(config.getQueueCapacity());

        this.metricRegistry = metricRegistry;
        this.pendingNotifications = metricRegistry.register(MetricRegistry.name(NotificationQueueDispatcher.class, "pending-notifications"),
                                                            new Gauge<Integer>() {
                                                                @Override
                                                                public Integer getValue() {
                                                                    return pendingNotificationsQ.size();
                                                                }
                                                            });

        this.runners = new NotificationRunner[config.getNbThreads()];
        for (int i = 0; i < config.getNbThreads(); i++) {
            runners[i] = new NotificationRunner(pendingNotificationsQ, clock, config, objectMapper, nbProcessedEvents, queues, dao, perQueueProcessingTime, metricRegistry, processedNotificationsSinceStart);
        }
    }

    @Override
    public boolean startQueue() {
        if (super.startQueue()) {
            for (int i = 0; i < config.getNbThreads(); i++) {
                executor.execute(runners[i]);
            }
            return true;
        }
        return false;
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
            for (int i = 0; i < config.getNbThreads(); i++) {
                try {
                    runners[i].stop();
                } catch (final Exception e) {
                    log.warn("Failed to stop Notification runner {} {}", i, e);
                }
            }
        }
    }

    public Clock getClock() {
        return clock;
    }


    @Override
    public int doProcessEvents() {
        return doProcessEventsWithLimit(-1);
    }

    protected int doProcessEventsWithLimit(final int limit) {

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
        for (final NotificationEventModelDao cur : notifications) {
            try {
                pendingNotificationsQ.put(cur);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("NotificationQueueDispatcher thread got interrupted");
                return 0;
            }
        }
        return notifications.size();
    }

    public static class NotificationRunner implements Runnable {

        private final LinkedBlockingQueue<NotificationEventModelDao> pendingNotificationsQ;
        private final Clock clock;
        private final NotificationQueueConfig config;
        private final ObjectMapper objectMapper;
        private final AtomicLong nbProcessedEvents;
        private final Map<String, NotificationQueue> queues;
        private final DBBackedQueue<NotificationEventModelDao> dao;
        private final Map<String, Histogram> perQueueProcessingTime;
        private final MetricRegistry metricRegistry;
        private final Counter processedNotificationsSinceStart;
        private final AtomicBoolean isProcessingevents;
        private final AtomicBoolean isExited;

        private String LOG_PREFIX;
        private Thread runnerTh;

        public NotificationRunner(final LinkedBlockingQueue<NotificationEventModelDao> pendingNotificationsQ,
                                  final Clock clock,
                                  final NotificationQueueConfig config,
                                  final ObjectMapper objectMapper,
                                  final AtomicLong nbProcessedEvents,
                                  final Map<String, NotificationQueue> queues,
                                  final DBBackedQueue<NotificationEventModelDao> dao,
                                  final Map<String, Histogram> perQueueProcessingTime,
                                  final MetricRegistry metricRegistry,
                                  final Counter processedNotificationsSinceStart) {
            this.pendingNotificationsQ = pendingNotificationsQ;
            this.clock = clock;
            this.config = config;
            this.objectMapper = objectMapper;
            this.nbProcessedEvents = nbProcessedEvents;
            this.queues = queues;
            this.dao = dao;
            this.perQueueProcessingTime = perQueueProcessingTime;
            this.metricRegistry = metricRegistry;
            this.processedNotificationsSinceStart = processedNotificationsSinceStart;
            this.isProcessingevents = new AtomicBoolean(false);
            this.isExited = new AtomicBoolean(false);
        }

        @Override
        public void run() {

            this.LOG_PREFIX = "NotificationRunner " + Thread.currentThread().getName() + "-" + Thread.currentThread().getId() + ": ";

            if (!isProcessingevents.compareAndSet(false, true)) {
                log.warn(LOG_PREFIX + "is already running");
                return;
            }

            this.runnerTh = Thread.currentThread();

            log.info(LOG_PREFIX + "starting...");
            do {
                try {
                    final NotificationEventModelDao notification = pendingNotificationsQ.poll(1, TimeUnit.SECONDS);
                    if (notification != null) {
                        nbProcessedEvents.incrementAndGet();
                        final NotificationEvent key = deserializeEvent(notification.getClassName(), objectMapper, notification.getEventJson());

                        final NotificationQueueHandler handler = getHandlerForActiveQueue(notification.getQueueName());
                        if (handler == null) {
                            log.warn("Cannot find handler for notification: queue = {}, record_id = {}",
                                     notification.getQueueName(),
                                     notification.getRecordId());
                            continue;
                        }

                        NotificationQueueException lastException = null;
                        long errorCount = notification.getErrorCount();
                        try {
                            handleNotificationWithMetrics(handler, notification, key);
                        } catch (final NotificationQueueException e) {
                            lastException = e;
                            errorCount++;
                        } finally {
                            if (lastException == null) {
                                clearNotification(notification);
                                if (log.isDebugEnabled()) {
                                    log.debug(LOG_PREFIX + "done handling notification %s, key = %s for time %s", notification.getRecordId(), notification.getEventJson(), notification.getEffectiveDate());
                                }
                            } else if (errorCount <= config.getMaxFailureRetries()) {
                                log.info(LOG_PREFIX + "dispatch error, will attempt a retry ", lastException);
                                final NotificationEventModelDao failedNotification = new NotificationEventModelDao(notification, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.AVAILABLE, errorCount);
                                dao.updateOnError(failedNotification);
                            } else {
                                log.error(LOG_PREFIX + "fatal NotificationQ dispatch error, data corruption...", lastException);
                                clearFailedNotification(notification);
                            }
                        }
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info(LOG_PREFIX + "got interrupted ");
                    break;
                }
            } while (isProcessingevents.get());
            log.info(LOG_PREFIX + "exiting loop...");
            isExited.set(true);
            synchronized (this) {
                notifyAll();
            }
        }

        public void stop() {

            isProcessingevents.set(false);
            runnerTh.interrupt();

            try {
                final long ini = System.currentTimeMillis();
                long remainingWaitTimeMs = waitTimeoutMs;
                synchronized (this) {
                    while (!isExited.get() && remainingWaitTimeMs > 0) {
                        wait(100);
                        remainingWaitTimeMs = waitTimeoutMs - (System.currentTimeMillis() - ini);
                    }
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Got interrupted while stopping " + LOG_PREFIX);
            }
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

            Histogram perQueueHistogramProcessingTime = perQueueProcessingTime.get(notification.getQueueName());
            if (perQueueHistogramProcessingTime == null) {
                synchronized (perQueueProcessingTime) {
                    if (!perQueueProcessingTime.containsKey(notification.getQueueName())) {
                        perQueueProcessingTime.put(notification.getQueueName(), metricRegistry.histogram(MetricRegistry.name(NotificationQueueDispatcher.class, metricName)));
                    }
                    perQueueHistogramProcessingTime = perQueueProcessingTime.get(notification.getQueueName());
                }
            }
            final DateTime beforeProcessing = clock.getUTCNow();

            try {
                handler.handleReadyNotification(key, notification.getEffectiveDate(), notification.getFutureUserToken(), notification.getSearchKey1(), notification.getSearchKey2());
            } catch (final RuntimeException e) {
                throw new NotificationQueueException(e);
            } finally {
                // Unclear if those stats should include failures
                final DateTime afterProcessing = clock.getUTCNow();
                perQueueHistogramProcessingTime.update(afterProcessing.getMillis() - beforeProcessing.getMillis());
                processedNotificationsSinceStart.inc();
            }
        }

        private void clearNotification(final NotificationEventModelDao cleared) {
            final NotificationEventModelDao processedEntry = new NotificationEventModelDao(cleared, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED);
            dao.moveEntryToHistory(processedEntry);
        }

        private void clearFailedNotification(final NotificationEventModelDao cleared) {
            final NotificationEventModelDao processedEntry = new NotificationEventModelDao(cleared, Hostname.get(), clock.getUTCNow(), PersistentQueueEntryLifecycleState.FAILED);
            dao.moveEntryToHistory(processedEntry);
        }

        private NotificationQueueHandler getHandlerForActiveQueue(final String compositeName) {
            final NotificationQueue queue = queues.get(compositeName);
            if (queue == null || !queue.isStarted()) {
                return null;
            }
            return queue.getHandler();
        }
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
