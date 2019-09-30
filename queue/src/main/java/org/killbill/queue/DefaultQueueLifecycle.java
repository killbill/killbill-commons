/*
 * Copyright 2010-2011 Ning, Inc.
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
    private volatile boolean isProcessingEvents;
    // Deferred in start sequence to allow for restart, which is not possible after the shutdown (mostly for test purpose)
    private ExecutorService executor;

    public DefaultQueueLifecycle(final String svcQName, final PersistentQueueConfig config, final MetricRegistry metricRegistry) {
        this(svcQName, config, metricRegistry, QueueObjectMapper.get());
    }
    private DefaultQueueLifecycle(final String svcQName, final PersistentQueueConfig config, final MetricRegistry metricRegistry, final ObjectMapper objectMapper) {
        this.svcQName = svcQName;
        this.config = config;
        this.isProcessingEvents = false;
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

        this.executor = Executors.newFixedThreadPool(config.geNbLifecycleDispatchThreads() + config.geNbLifecycleCompleteThreads(),
                                                     config.getTableName() + "-lifecycle-th");

        log.info(String.format("%s: Starting...", svcQName));

        isProcessingEvents = true;

        for (int i = 0; i < config.geNbLifecycleDispatchThreads(); i++) {
            executor.execute(new DispatcherRunnable());
        }

        for (int i = 0; i < config.geNbLifecycleCompleteThreads(); i++) {
            executor.execute(new CompletionRunnable());
        }
        return true;
    }

    @Override
    public void stopQueue() {
        this.isProcessingEvents = false;

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            log.info(String.format("%s: Stop sequence has been interrupted", svcQName));
        } finally {
            int remainingCompleted = completedOrFailedEvents.size();
            int remainingRetried = retriedEvents.size();
            if (remainingCompleted > 0 || remainingRetried > 0) {
                log.warn(String.format("%s: Stopped queue with %d event/notifications non completed ", svcQName, (remainingCompleted + remainingRetried)));
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
                log.info(String.format("%s: Thread %s-completion [%d] starting ",
                                       svcQName,
                                       Thread.currentThread().getName(),
                                       Thread.currentThread().getId()));

                while (true) {

                    if (!isProcessingEvents) {
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
                log.info(String.format("%s: Thread %s got interrupted, exiting... ", svcQName, Thread.currentThread().getName()));
            } catch (final Error e) {
                log.error(String.format("%s: Thread %s got an exception, exiting... ", svcQName, Thread.currentThread().getName()), e);
            } finally {
                log.info(String.format("%s: Thread %s has exited", svcQName, Thread.currentThread().getName()));
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
                log.info(String.format("%s: Thread %s-dispatcher [%d] starting ",
                                       svcQName,
                                       Thread.currentThread().getName(),
                                       Thread.currentThread().getId()));

                while (true) {

                    if (!isProcessingEvents) {
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
                log.info(String.format("%s: Thread %s got interrupted, exiting... ", svcQName, Thread.currentThread().getName()));
            } catch (final Error e) {
                log.error(String.format("%s: Thread %s got an exception, exiting... ", svcQName, Thread.currentThread().getName()), e);
            } finally {
                log.info(String.format("%s: Thread %s has exited", svcQName, Thread.currentThread().getName()));
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
            log.warn(String.format("%s: Thread %s got DBIException exception: %s",
                                   svcQName, Thread.currentThread().getName(), e));
        } catch (final RuntimeException e) {
            log.warn(String.format("%s: Thread %s got Runtime exception: %s",
                                   svcQName, Thread.currentThread().getName(), e));
        }
    }

}
