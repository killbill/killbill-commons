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
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.killbill.commons.concurrent.Executors;
import org.killbill.queue.api.PersistentQueueConfig;
import org.killbill.queue.api.QueueLifecycle;
import org.killbill.queue.dao.EventEntryModelDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;

public abstract class DefaultQueueLifecycle implements QueueLifecycle {

    public static final String QUEUE_NAME = "Queue";

    private static final Logger log = LoggerFactory.getLogger(DefaultQueueLifecycle.class);

    private static final long ONE_MILLION = 1000L * 1000L;

    // Max sleep on the notificationQ before going through the full loop
    private static final long MAX_SLEEP_TIME_MS = 100;

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

        this.dispatchTime = metricRegistry.timer(MetricRegistry.name(DefaultQueueLifecycle.class, "dispatchTime"));
        this.completeTime = metricRegistry.timer(MetricRegistry.name(DefaultQueueLifecycle.class, "completeTime"));

        this.dispatchedEntries = metricRegistry.histogram(MetricRegistry.name(DefaultQueueLifecycle.class, "dispatchedEntries"));
        this.completeEntries = metricRegistry.histogram(MetricRegistry.name(DefaultQueueLifecycle.class, "completeEntries"));

        metricRegistry.register(MetricRegistry.name(DefaultQueueLifecycle.class, "completedOrFailedEvents", "size"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return completedOrFailedEvents.size();
            }
        });
    }


    private static final int DISPATCH_MASK = 0x1;
    private static final int COMPLETE_MASK = 0x2;

    private enum DispatchCompleteMode {
        SINGLE_THREAD,
        SEPARATE_THREAD
    }

    // TODO eventually may land in the PersistentQueueConfig if we decide to keep both modes.
    private static DispatchCompleteMode getDispatchCompleteMode() {
        final String prop = MoreObjects.firstNonNull(System.getProperty("org.killbill.persistent.bus.complete.mode"), DispatchCompleteMode.SINGLE_THREAD.name());
        return DispatchCompleteMode.valueOf(prop);
    }

    @Override
    public boolean startQueue() {

        final DispatchCompleteMode dispatchCompleteMode  = getDispatchCompleteMode();
        int nbThreads = dispatchCompleteMode == DispatchCompleteMode.SINGLE_THREAD ? 1 : 2;

        final Exchanger<Long> exchanger = new Exchanger<Long>();

        this.executor = Executors.newFixedThreadPool(nbThreads, config.getTableName() + "-lifecycle-th");
        isProcessingEvents = true;

        log.info(String.format("%s: Starting...", svcQName));

        for (int i = 0; i < nbThreads; i++) {
            executor.execute(new Runnable() {


                private int getActionMask() throws InterruptedException {
                    if (dispatchCompleteMode == DispatchCompleteMode.SINGLE_THREAD) {
                        return DISPATCH_MASK | COMPLETE_MASK;
                    } else /* SEPARATE_THREAD */ {
                        // Run exchange logic to decide who is who...
                        final long myThreadId = Thread.currentThread().getId();
                        final long otherThreadId = exchanger.exchange(myThreadId);
                        return myThreadId < otherThreadId ? DISPATCH_MASK : COMPLETE_MASK;
                    }
                }

                @Override
                public void run() {


                    try {
                        final int actionMask = getActionMask();

                        log.info(String.format("%s: Thread %s [%d] starting with actionMask = %d",
                                               svcQName,
                                               Thread.currentThread().getName(),
                                               Thread.currentThread().getId(),
                                               actionMask));


                        while (true) {

                            // Delete/update completed/errored entries from DB
                            completeOrRetryProcessedEvents(actionMask);

                            if (!isProcessingEvents) {
                                break;
                            }

                            final long beforeLoop = System.nanoTime();
                            dispatchEvents(actionMask);
                            final long afterLoop = System.nanoTime();

                            completeOrRetryProcessedEvents(actionMask);

                            sleepSporadically((afterLoop - beforeLoop) / ONE_MILLION, actionMask);
                        }
                    } catch (final InterruptedException e) {
                        log.info(String.format("%s: Thread %s got interrupted, exting... ", svcQName, Thread.currentThread().getName()));
                    } catch (final Throwable e) {
                        log.error(String.format("%s: Thread %s got an exception, exting... ", svcQName, Thread.currentThread().getName()), e);
                    } finally {
                        log.info(String.format("%s: Thread %s has exited", svcQName, Thread.currentThread().getName()));
                    }
                }

                private void dispatchEvents(int actionMask) {

                    if ((actionMask & DISPATCH_MASK) != DISPATCH_MASK) {
                        return;
                    }


                    long ini = System.nanoTime();
                    final DispatchResultMetrics metricsResult = doDispatchEvents();
                    dispatchedEntries.update(metricsResult.getNbEntries());
                    if (isStickyEvent) {
                        dispatchTime.update(metricsResult.getTimeNanoSec(), TimeUnit.NANOSECONDS);
                    } else {
                        dispatchTime.update(System.nanoTime() - ini, TimeUnit.NANOSECONDS);
                    }
                }

                private void completeOrRetryProcessedEvents(int actionMask) {

                    if ((actionMask & COMPLETE_MASK) != COMPLETE_MASK) {
                        return;
                    }

                    long ini = System.nanoTime();
                    int completed = drainCompletedEvents();
                    int retried = drainRetriedEvents();
                    final int completeOrRetried = completed + retried;
                    if (completeOrRetried > 0) {
                        completeEntries.update(completeOrRetried);
                        completeTime.update(System.nanoTime() - ini, TimeUnit.NANOSECONDS);
                    }
                }

                // Move completed entries through batches using the same main DB (lifecycle) thread
                private int drainCompletedEvents() {
                    int curSize = completedOrFailedEvents.size();
                    if (curSize > 0) {
                        final List<EventEntryModelDao> completed = new ArrayList<>(curSize);
                        completedOrFailedEvents.drainTo(completed, curSize);
                        doProcessCompletedEvents(completed);
                    }
                    return curSize;
                }

                // Move retried entries using the same main DB (lifecycle) thread
                private int drainRetriedEvents() {
                    final int curSize = retriedEvents.size();
                    if (curSize > 0) {
                        final List<EventEntryModelDao> retried = new ArrayList<>(curSize);
                        retriedEvents.drainTo(retried, curSize);
                        doProcessRetriedEvents(retried);
                    }
                    return curSize;
                }

                private void sleepSporadically(final long loopTimeMsec, int actionMask) throws InterruptedException {
                    if (isStickyEvent) {
                        // In this mode, the main thread does not sleep, but blocks on the inflightQ to minimize latency.
                        return;
                    }

                    long remainingSleepTime = config.getPollingSleepTimeMs() - loopTimeMsec;
                    while (remainingSleepTime > 0) {
                        final long curSleepTime =  remainingSleepTime > MAX_SLEEP_TIME_MS ? MAX_SLEEP_TIME_MS : remainingSleepTime;
                        Thread.sleep(curSleepTime);
                        // Each loop we verify if we have new entries to complete
                        completeOrRetryProcessedEvents(actionMask);
                        remainingSleepTime -= curSleepTime;
                    }
                }
            });
        }
        return true;
    }

    @Override
    public void stopQueue() {
        this.isProcessingEvents = false;

        executor.shutdown();
        try {
            executor.awaitTermination(config.getPollingSleepTimeMs(), TimeUnit.SECONDS);
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
}
