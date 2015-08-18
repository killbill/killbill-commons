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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.killbill.commons.concurrent.Executors;
import org.killbill.queue.api.PersistentQueueConfig;
import org.killbill.queue.api.QueueLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public abstract class DefaultQueueLifecycle implements QueueLifecycle {

    public static final String QUEUE_NAME = "Queue";

    private static final Logger log = LoggerFactory.getLogger(DefaultQueueLifecycle.class);

    private final static long ONE_MILLION = 1000L * 1000L;

    protected final String svcQName;
    protected final ObjectMapper objectMapper;
    protected final PersistentQueueConfig config;
    protected final AtomicBoolean isStarted = new AtomicBoolean(false);

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
    }

    @Override
    public boolean startQueue() {
        this.executor = Executors.newFixedThreadPool(1, config.getTableName() + "-lifecycle-th");
        if (config.isProcessingOff() || !isStarted.compareAndSet(false, true)) {
            return false;
        }
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
                            doProcessEvents();
                        } catch (Exception e) {
                            log.warn(String.format("%s: Thread  %s  [%d] got an exception, catching and moving on...",
                                    svcQName,
                                    Thread.currentThread().getName(),
                                    Thread.currentThread().getId()), e);
                        } finally {
                            final long afterLoop = System.nanoTime();
                            sleepALittle((afterLoop - beforeLoop) / ONE_MILLION);
                        }
                    }
                } catch (InterruptedException e) {
                    log.info(String.format("%s: Thread %s got interrupted, exting... ", svcQName, Thread.currentThread().getName()));
                } catch (Throwable e) {
                    log.error(String.format("%s: Thread %s got an exception, exting... ", svcQName, Thread.currentThread().getName()), e);
                } finally {
                    log.info(String.format("%s: Thread %s has exited", svcQName, Thread.currentThread().getName()));
                }
            }

            private void sleepALittle(long loopTimeMsec) throws InterruptedException {
                if (config.getPersistentQueueMode() == PersistentQueueConfig.PersistentQueueMode.STICKY_EVENTS) {
                    // Disregard config.getPollingSleepTimeMs() in that mode in case this is not correctky configured with 0
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
        if (config.isProcessingOff() || !isStarted.compareAndSet(true, false)) {
            return;
        }
        this.isProcessingEvents = false;

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.info(String.format("%s: Stop sequence has been interrupted", svcQName));
        }
    }

    public abstract int doProcessEvents();

    public boolean isStarted() {
        return isStarted.get();
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
