/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.killbill.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.killbill.commons.concurrent.Executors;
import org.killbill.queue.api.PersistentQueueConfig;
import org.killbill.queue.api.QueueLifecycle;
import org.killbill.queue.dao.EventEntryModelDao;
import org.skife.jdbi.v2.exceptions.DBIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class DefaultQueueLifecycle implements QueueLifecycle {

    public static final String QUEUE_NAME = "Queue";

    private static final Logger log = LoggerFactory.getLogger(DefaultQueueLifecycle.class);

    private static final long ONE_MILLION = 1000L * 1000L;

    private static final long MAX_SLEEP_TIME_MS = 100;

    // Max size of the batch we allow
    private static final int MAX_COMPLETED_ENTRIES = 15;

    protected final String svcQName;
    protected final ObjectMapper objectMapper;
    protected final PersistentQueueConfig config;
    private final LinkedBlockingQueue<EventEntryModelDao> completedOrFailedEvents;
    private final LinkedBlockingQueue<EventEntryModelDao> retriedEvents;
    // Time to dispatch entries to Dispatcher threads
    private final Timer dispatchTime;
    // Time to move entries to history table (or update entry for retry)
    private final Timer completeTime;
    // Nb of entries dispatched at each loop
    private final Histogram dispatchedEntries;
    // Nb of entries completed at each loop
    private final Histogram completeEntries;
    private final boolean isStickyEvent;
    private volatile boolean isDispatchingEvents;
    private volatile boolean isCompletingEvents;
    // Deferred in start sequence to allow for restart, which is not possible after the shutdown (mostly for test purpose)
    private ExecutorService lifecycleDispatcherExecutor;
    private ExecutorService lifecycleCompletionExecutor;

    public DefaultQueueLifecycle(final String svcQName,
                                 final PersistentQueueConfig config,
                                 final MetricRegistry metricRegistry) {
        this(svcQName, config, metricRegistry, QueueObjectMapper.get());
    }

    private DefaultQueueLifecycle(final String svcQName,
                                  final PersistentQueueConfig config,
                                  final MetricRegistry metricRegistry,
                                  final ObjectMapper objectMapper) {
        this.svcQName = svcQName;
        this.config = config;
        this.isDispatchingEvents = false;
        this.isCompletingEvents = false;
        this.objectMapper = objectMapper;
        this.completedOrFailedEvents = new LinkedBlockingQueue<>();
        this.retriedEvents = new LinkedBlockingQueue<>();
        this.isStickyEvent = config.getPersistentQueueMode() == PersistentQueueConfig.PersistentQueueMode.STICKY_EVENTS;

        this.dispatchTime = metricRegistry.timer(MetricRegistry.name(DefaultQueueLifecycle.class, svcQName, "dispatchTime"));
        this.completeTime = metricRegistry.timer(MetricRegistry.name(DefaultQueueLifecycle.class, svcQName, "completeTime"));

        this.dispatchedEntries = metricRegistry.histogram(MetricRegistry.name(DefaultQueueLifecycle.class, svcQName, "dispatchedEntries"));
        this.completeEntries = metricRegistry.histogram(MetricRegistry.name(DefaultQueueLifecycle.class, svcQName, "completeEntries"));

        metricRegistry.register(MetricRegistry.name(DefaultQueueLifecycle.class, svcQName, "completedOrFailedEvents", "size"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return completedOrFailedEvents.size();
            }
        });
    }

    @Override
    public boolean startQueue() {
        this.lifecycleDispatcherExecutor = Executors.newFixedThreadPool(config.geNbLifecycleDispatchThreads(),
                                                                        config.getTableName() + "-lifecycle-dispatcher-th");
        this.lifecycleCompletionExecutor = Executors.newFixedThreadPool(config.geNbLifecycleCompleteThreads(),
                                                                        config.getTableName() + "-lifecycle-completion-th");

        log.info("{}: Starting...", svcQName);

        // Start the completion threads before the dispatcher ones
        isCompletingEvents = true;
        for (int i = 0; i < config.geNbLifecycleCompleteThreads(); i++) {
            lifecycleCompletionExecutor.execute(new CompletionRunnable());
        }

        isDispatchingEvents = true;
        for (int i = 0; i < config.geNbLifecycleDispatchThreads(); i++) {
            lifecycleDispatcherExecutor.execute(new DispatcherRunnable());
        }

        return true;
    }

    // Stop the lifecycle dispatcher threads, which fetch available entries and move them into the dispatch queue
    protected boolean stopLifecycleDispatcher() {
        isDispatchingEvents = false;

        lifecycleDispatcherExecutor.shutdown();
        try {
            return lifecycleDispatcherExecutor.awaitTermination(config.getShutdownTimeout().getPeriod(), config.getShutdownTimeout().getUnit());
        } catch (final InterruptedException e) {
            log.info("{}: Lifecycle dispatcher stop sequence has been interrupted", svcQName);
            return false;
        }
    }

    // Stop the lifecycle completion threads, which move processed and failed entries into the history tables
    protected boolean stopLifecycleCompletion() {
        isCompletingEvents = false;

        lifecycleCompletionExecutor.shutdown();
        try {
            return lifecycleCompletionExecutor.awaitTermination(config.getShutdownTimeout().getPeriod(), config.getShutdownTimeout().getUnit());
        } catch (final InterruptedException e) {
            log.info("{}: Lifecycle completion stop sequence has been interrupted", svcQName);
            return false;
        } finally {
            final int remainingCompleted = completedOrFailedEvents.size();
            final int remainingRetried = retriedEvents.size();
            if (remainingCompleted > 0 || remainingRetried > 0) {
                log.warn("{}: Stopped queue with {} event/notifications non completed", svcQName, (remainingCompleted + remainingRetried));
            }
        }
    }

    public <M extends EventEntryModelDao> void dispatchCompletedOrFailedEvents(final M event) {
        completedOrFailedEvents.add(event);
    }

    public <M extends EventEntryModelDao> void dispatchRetriedEvents(final M event) {
        retriedEvents.add(event);
    }

    public abstract DispatchResultMetrics doDispatchEvents();

    public abstract void doProcessCompletedEvents(final Iterable<? extends EventEntryModelDao> completed);

    public abstract void doProcessRetriedEvents(final Iterable<? extends EventEntryModelDao> retried);

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public static class DispatchResultMetrics {

        private final int nbEntries;
        private final long timeNanoSec;

        public DispatchResultMetrics(final int nbEntries, final long timeNanoSec) {
            this.nbEntries = nbEntries;
            this.timeNanoSec = timeNanoSec;
        }

        public int getNbEntries() {
            return nbEntries;
        }

        public long getTimeNanoSec() {
            return timeNanoSec;
        }
    }

    private final class CompletionRunnable implements Runnable {

        @Override
        public void run() {
            try {
                log.info("{}: Completion thread {} [{}] starting ",
                         svcQName,
                         Thread.currentThread().getName(),
                         Thread.currentThread().getId());

                while (true) {

                    if (!isCompletingEvents) {
                        break;
                    }

                    withHandlingRuntimeException(new RunnableRawCallback() {
                        @Override
                        public void callback() throws InterruptedException {
                            long ini = System.nanoTime();
                            long pollSleepTime = 0;
                            final List<EventEntryModelDao> completed = new ArrayList<>(MAX_COMPLETED_ENTRIES);
                            completedOrFailedEvents.drainTo(completed, MAX_COMPLETED_ENTRIES);
                            if (completed.isEmpty()) {
                                long beforePollTime = System.nanoTime();
                                final EventEntryModelDao entry = completedOrFailedEvents.poll(MAX_SLEEP_TIME_MS, TimeUnit.MILLISECONDS);
                                pollSleepTime = System.nanoTime() - beforePollTime;
                                if (entry != null) {
                                    completed.add(entry);
                                }
                            }

                            if (!completed.isEmpty()) {
                                doProcessCompletedEvents(completed);
                            }

                            int retried = drainRetriedEvents();
                            final int completeOrRetried = completed.size() + retried;
                            if (completeOrRetried > 0) {
                                completeEntries.update(completeOrRetried);
                                completeTime.update((System.nanoTime() - ini) - pollSleepTime, TimeUnit.NANOSECONDS);
                            }
                        }
                    });
                }
            } catch (final InterruptedException e) {
                log.info("{}: Completion thread {} [{}] got interrupted, exiting... ",
                         svcQName,
                         Thread.currentThread().getName(),
                         Thread.currentThread().getId());
            } catch (final Error e) {
                log.error("{}: Completion thread {} [{}] got an exception, exiting...",
                          svcQName,
                          Thread.currentThread().getName(),
                          Thread.currentThread().getId(),
                          e);
            } finally {
                log.info("{}: Completion thread {} [{}] has exited",
                         svcQName,
                         Thread.currentThread().getName(),
                         Thread.currentThread().getId());
            }
        }

        private int drainRetriedEvents() {
            final int curSize = retriedEvents.size();
            if (curSize > 0) {
                final List<EventEntryModelDao> retried = new ArrayList<>(curSize);
                retriedEvents.drainTo(retried, curSize);
                doProcessRetriedEvents(retried);
            }
            return curSize;
        }
    }

    private final class DispatcherRunnable implements Runnable {

        @Override
        public void run() {
            try {
                log.info("{}: Dispatching thread {} [{}] starting ",
                         svcQName,
                         Thread.currentThread().getName(),
                         Thread.currentThread().getId());

                while (true) {

                    if (!isDispatchingEvents) {
                        break;
                    }

                    withHandlingRuntimeException(new RunnableRawCallback() {
                        @Override
                        public void callback() throws InterruptedException {
                            final long beforeLoop = System.nanoTime();
                            dispatchEvents();
                            final long afterLoop = System.nanoTime();

                            sleepSporadically((afterLoop - beforeLoop) / ONE_MILLION);
                        }
                    });
                }
            } catch (final InterruptedException e) {
                log.info("{}: Dispatching thread {} [{}] got interrupted, exiting... ",
                         svcQName,
                         Thread.currentThread().getName(),
                         Thread.currentThread().getId());
            } catch (final Error e) {
                log.error("{}: Dispatching thread {} [{}] got an exception, exiting... ",
                          svcQName,
                          Thread.currentThread().getName(),
                          Thread.currentThread().getId(),
                          e);
            } finally {
                log.info("{}: Dispatching thread {} [{}] has exited",
                         svcQName,
                         Thread.currentThread().getName(),
                         Thread.currentThread().getId());
            }
        }


        private void dispatchEvents() {

            long ini = System.nanoTime();
            final DispatchResultMetrics metricsResult = doDispatchEvents();
            dispatchedEntries.update(metricsResult.getNbEntries());
            if (isStickyEvent) {
                dispatchTime.update(metricsResult.getTimeNanoSec(), TimeUnit.NANOSECONDS);
            } else {
                dispatchTime.update(System.nanoTime() - ini, TimeUnit.NANOSECONDS);
            }
        }

        private void sleepSporadically(final long loopTimeMsec) throws InterruptedException {
            if (isStickyEvent) {
                // In this mode, the main thread does not sleep, but blocks on the inflightQ to minimize latency.
                return;
            }

            long remainingSleepTime = config.getPollingSleepTimeMs() - loopTimeMsec;
            while (remainingSleepTime > 0) {
                final long curSleepTime = remainingSleepTime > MAX_SLEEP_TIME_MS ? MAX_SLEEP_TIME_MS : remainingSleepTime;
                Thread.sleep(curSleepTime);
                remainingSleepTime -= curSleepTime;
            }
        }

    }


    private interface RunnableRawCallback {
        void callback() throws InterruptedException;
    }

    private void withHandlingRuntimeException(final RunnableRawCallback cb) throws InterruptedException {
        try {
            cb.callback();
        } catch (final DBIException e) {
            log.warn("{}: Thread {} got DBIException exception: ",
                                   svcQName, Thread.currentThread().getName(), e);
        } catch (final RuntimeException e) {
            log.warn("{}: Thread {} got Runtime exception: ",
                                   svcQName, Thread.currentThread().getName(), e);
        }
    }

}
