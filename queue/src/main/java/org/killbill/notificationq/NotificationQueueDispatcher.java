/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.notificationq;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.DateTime;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueConfig;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.killbill.notificationq.dao.NotificationEventModelDao;
import org.killbill.notificationq.dao.NotificationSqlDao;
import org.killbill.notificationq.dispatching.NotificationCallableCallback;
import org.killbill.queue.DBBackedQueue;
import org.killbill.queue.DBBackedQueueWithPolling;
import org.killbill.queue.DefaultQueueLifecycle;
import org.killbill.queue.api.PersistentQueueConfig.PersistentQueueMode;
import org.killbill.queue.dao.EventEntryModelDao;
import org.killbill.queue.dispatching.BlockingRejectionExecutionHandler;
import org.killbill.queue.dispatching.Dispatcher;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;

public class NotificationQueueDispatcher extends DefaultQueueLifecycle {

    protected static final Logger log = LoggerFactory.getLogger(NotificationQueueDispatcher.class);

    public static final int CLAIM_TIME_MS = (5 * 60 * 1000); // 5 minutes

    private final AtomicLong nbProcessedEvents;

    protected final NotificationQueueConfig config;
    protected final Clock clock;
    protected final Map<String, NotificationQueue> queues;
    protected final DBBackedQueue<NotificationEventModelDao> dao;
    protected final MetricRegistry metricRegistry;

    private final Counter processedNotificationsSinceStart;
    private final Map<String, Histogram> perQueueProcessingTime;

    // We could event have one per queue is required...
    private final Dispatcher<NotificationEvent, NotificationEventModelDao> dispatcher;
    private final AtomicBoolean isStarted;

    private final NotificationCallableCallback notificationCallableCallback;

    private final NotificationReaper reaper;

    // Package visibility on purpose
    NotificationQueueDispatcher(final Clock clock, final NotificationQueueConfig config, final IDBI dbi, final MetricRegistry metricRegistry) {
        super("NotificationQ", config);
        final ThreadFactory notificationQThreadFactory = new ThreadFactory() {
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
        };

        this.clock = clock;
        this.config = config;
        this.nbProcessedEvents = new AtomicLong();
        this.dao = new DBBackedQueueWithPolling<NotificationEventModelDao>(clock, dbi, NotificationSqlDao.class, config, "notif-" + config.getTableName(), metricRegistry);

        this.queues = new TreeMap<String, NotificationQueue>();

        this.processedNotificationsSinceStart = metricRegistry.counter(MetricRegistry.name(NotificationQueueDispatcher.class, "processed-notifications-since-start"));
        this.perQueueProcessingTime = new HashMap<String, Histogram>();

        this.metricRegistry = metricRegistry;
        this.isStarted = new AtomicBoolean(false);
        this.reaper = new NotificationReaper(this.dao, config, clock);

        this.notificationCallableCallback = new NotificationCallableCallback(this);
        this.dispatcher = new Dispatcher<>(1, config, 10, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>(config.getEventQueueCapacity()), notificationQThreadFactory, new BlockingRejectionExecutionHandler(),
                                           clock, notificationCallableCallback, this);
    }

    @Override
    public boolean startQueue() {
        if (isStarted.compareAndSet(false, true)) {
            dao.initialize();
            dispatcher.start();
            super.startQueue();
            return true;
        }

        if (config.getPersistentQueueMode() == PersistentQueueMode.STICKY_POLLING) {
            reaper.start();
        }
        return false;
    }

    @Override
    public void stopQueue() {
        if (!isStarted()) {
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
            dispatcher.stop();
            super.stopQueue();
            isStarted.set(false);
        }

        reaper.stop();
    }

    @Override
    public boolean isStarted() {
        return isStarted.get();
    }

    @Override
    public int doProcessEvents() {
        final List<NotificationEventModelDao> notifications = getReadyNotifications();
        if (notifications.isEmpty()) {
            return 0;
        }
        log.debug("Notifications from {} to process: {}", config.getTableName(), notifications);

        for (final NotificationEventModelDao cur : notifications) {
            dispatcher.dispatch(cur);
        }
        return notifications.size();
    }

    @Override
    public void doProcessCompletedEvents(final Iterable<? extends EventEntryModelDao> completed) {
        notificationCallableCallback.moveCompletedOrFailedEvents((Iterable<NotificationEventModelDao>) completed);
    }

    @Override
    public void doProcessRetriedEvents(final Iterable<? extends EventEntryModelDao> retried) {
        Iterator<? extends EventEntryModelDao> it = retried.iterator();
        while (it.hasNext()) {
            NotificationEventModelDao cur = (NotificationEventModelDao) it.next();
            notificationCallableCallback.updateRetriedEvents(cur);
        }
    }

    public void handleNotificationWithMetrics(final NotificationQueueHandler handler, final NotificationEventModelDao notification, final NotificationEvent key) throws NotificationQueueException {

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
            nbProcessedEvents.incrementAndGet();
            // Unclear if those stats should include failures
            final DateTime afterProcessing = clock.getUTCNow();
            perQueueHistogramProcessingTime.update(afterProcessing.getMillis() - beforeProcessing.getMillis());
            processedNotificationsSinceStart.inc();
        }
    }

    public NotificationQueueHandler getHandlerForActiveQueue(final String compositeName) {
        final NotificationQueue queue = queues.get(compositeName);
        if (queue == null || !queue.isStarted()) {
            return null;
        }
        return queue.getHandler();
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

    public static String getCompositeName(final String svcName, final String queueName) {
        return svcName + ":" + queueName;
    }

    public NotificationQueueConfig getConfig() {
        return config;
    }

    public Clock getClock() {
        return clock;
    }

    public DBBackedQueue<NotificationEventModelDao> getDao() {
        return dao;
    }
}
