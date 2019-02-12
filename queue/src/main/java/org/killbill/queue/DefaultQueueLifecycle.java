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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class DefaultQueueLifecycle implements QueueLifecycle {

    public static final String QUEUE_NAME = "Queue";

    private static final Logger log = LoggerFactory.getLogger(DefaultQueueLifecycle.class);

    private static final long ONE_MILLION = 1000L * 1000L;

    private static final int COMPLETED_BATCH_SIZE = 100;

    protected final String svcQName;
    protected final ObjectMapper objectMapper;
    protected final PersistentQueueConfig config;

    private final LinkedBlockingQueue<EventEntryModelDao> completedOrFailedEvents;
    private final LinkedBlockingQueue<EventEntryModelDao> retriedEvents;

    private volatile boolean isProcessingEvents;

    // Deferred in start sequence to allow for restart, which is not possible after the shutdown (mostly for test purpose)
    private ExecutorService executor;

    public DefaultQueueLifecycle(final String svcQName, final PersistentQueueConfig config) {
        this(svcQName, config, QueueObjectMapper.get());
    }

    private DefaultQueueLifecycle(final String svcQName, final PersistentQueueConfig config, final ObjectMapper objectMapper) {
        this.svcQName = svcQName;
        this.config = config;
        this.isProcessingEvents = false;
        this.objectMapper = objectMapper;
        this.completedOrFailedEvents = new LinkedBlockingQueue<>(config.getEventQueueCapacity());
        this.retriedEvents = new LinkedBlockingQueue<>(config.getEventQueueCapacity());
    }

    @Override
    public boolean startQueue() {
        this.executor = Executors.newFixedThreadPool(1, config.getTableName() + "-lifecycle-th");
        isProcessingEvents = true;

        log.info(String.format("%s: Starting...", svcQName));

        executor.execute(new Runnable() {
            @Override
            public void run() {

                log.info(String.format("%s: Thread %s [%d] starting",
                                       svcQName,
                                       Thread.currentThread().getName(),
                                       Thread.currentThread().getId()));

                try {
                    while (true) {
                        if (!isProcessingEvents) {
                            break;
                        }

                        final long beforeLoop = System.nanoTime();
                        try {
                            // Fetch ready entries from DB and dispatch them to thread pool
                            doProcessEvents();

                            // Delete/update completed/errored entries from DB
                            drainCompletedEvents();
                            drainRetriedEvents();

                        } catch (final Exception e) {
                            log.warn(String.format("%s: Thread  %s  [%d] got an exception, catching and moving on...",
                                                   svcQName,
                                                   Thread.currentThread().getName(),
                                                   Thread.currentThread().getId()), e);
                        } finally {
                            final long afterLoop = System.nanoTime();
                            sleepALittle((afterLoop - beforeLoop) / ONE_MILLION);
                        }
                    }
                } catch (final InterruptedException e) {
                    log.info(String.format("%s: Thread %s got interrupted, exting... ", svcQName, Thread.currentThread().getName()));
                } catch (final Throwable e) {
                    log.error(String.format("%s: Thread %s got an exception, exting... ", svcQName, Thread.currentThread().getName()), e);
                } finally {
                    log.info(String.format("%s: Thread %s has exited", svcQName, Thread.currentThread().getName()));
                }
            }

            // Move completed entries through batches using the same main DB (lifecycle) thread
            private void drainCompletedEvents() {
                final List<EventEntryModelDao> completed = new ArrayList<>(COMPLETED_BATCH_SIZE);
                int totalSize = completedOrFailedEvents.size();
                while (totalSize > 0) {
                    final int curSize = totalSize > COMPLETED_BATCH_SIZE ? COMPLETED_BATCH_SIZE : totalSize;
                    completedOrFailedEvents.drainTo(completed, curSize);
                    doProcessCompletedEvents(completed);
                    totalSize -= curSize;
                    completed.clear();
                }
            }

            // Move retried entries using the same main DB (lifecycle) thread
            private void drainRetriedEvents() {
                final int curSize = retriedEvents.size();
                if (curSize > 0) {
                    final List<EventEntryModelDao> retried = new ArrayList<>(curSize);
                    retriedEvents.drainTo(retried, curSize);
                    doProcessRetriedEvents(retried);
                }
            }

            private void sleepALittle(final long loopTimeMsec) throws InterruptedException {
                if (config.getPersistentQueueMode() == PersistentQueueConfig.PersistentQueueMode.STICKY_EVENTS) {
                    // Disregard config.getPollingSleepTimeMs() in that mode in case this is not correctly configured with 0
                    return;
                }
                final long remainingSleepTime = config.getPollingSleepTimeMs() - loopTimeMsec;
                if (remainingSleepTime > 0) {
                    Thread.sleep(remainingSleepTime);
                }
            }
        });
        return true;
    }

    @Override
    public void stopQueue() {
        this.isProcessingEvents = false;

        executor.shutdownNow();
        try {
            executor.awaitTermination(config.getPollingSleepTimeMs(), TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            log.info(String.format("%s: Stop sequence has been interrupted", svcQName));
        }
    }

    public <M extends EventEntryModelDao> void dispatchCompletedOrFailedEvents(final M event) {
        completedOrFailedEvents.add(event);
    }

    public <M extends EventEntryModelDao> void dispatchRetriedEvents(final M event) {
        retriedEvents.add(event);
    }

    public abstract int doProcessEvents();

    public abstract void doProcessCompletedEvents(final Iterable<? extends EventEntryModelDao> completed);

    public abstract void doProcessRetriedEvents(final Iterable<? extends EventEntryModelDao> retried);

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
