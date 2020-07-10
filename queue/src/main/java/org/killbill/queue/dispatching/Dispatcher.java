/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.queue.dispatching;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.killbill.clock.Clock;
import org.killbill.commons.concurrent.DynamicThreadPoolExecutorWithLoggingOnExceptions;
import org.killbill.queue.DefaultQueueLifecycle;
import org.killbill.queue.api.PersistentQueueConfig;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.api.QueueEvent;
import org.killbill.queue.dao.EventEntryModelDao;
import org.killbill.queue.retry.RetryableInternalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class Dispatcher<E extends QueueEvent, M extends EventEntryModelDao> {

    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);

    // Dynamic ThreadPool Executor
    private final int corePoolSize;
    private final int maximumPoolSize;
    private final long keepAliveTime;
    private final TimeUnit keepAliveTimeUnit;
    private final BlockingQueue<Runnable> workQueue;
    private final ThreadFactory threadFactory;
    private final RejectedExecutionHandler rejectionHandler;

    private final int maxFailureRetries;
    private final CallableCallback<E, M> handlerCallback;
    private final DefaultQueueLifecycle parentLifeCycle;
    private final Clock clock;

    // Deferred in start sequence to allow for restart, which is not possible after the shutdown (mostly for test purpose)
    private ExecutorService handlerExecutor;

    public Dispatcher(final int corePoolSize,
                      final PersistentQueueConfig config,
                      final long keepAliveTime,
                      final TimeUnit keepAliveTimeUnit,
                      final BlockingQueue<Runnable> workQueue,
                      final ThreadFactory threadFactory,
                      final RejectedExecutionHandler rejectionHandler,
                      final Clock clock,
                      final CallableCallback<E, M> handlerCallback,
                      final DefaultQueueLifecycle parentLifeCycle) {
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = config.geMaxDispatchThreads();
        this.keepAliveTime = keepAliveTime;
        this.keepAliveTimeUnit = keepAliveTimeUnit;
        this.workQueue = workQueue;
        this.threadFactory = threadFactory;
        this.rejectionHandler = rejectionHandler;

        this.clock = clock;
        this.maxFailureRetries = config.getMaxFailureRetries();
        this.handlerCallback = handlerCallback;
        this.parentLifeCycle = parentLifeCycle;
    }

    public void start() {
        this.handlerExecutor = new DynamicThreadPoolExecutorWithLoggingOnExceptions(corePoolSize, maximumPoolSize, keepAliveTime, keepAliveTimeUnit, workQueue, threadFactory, rejectionHandler);
    }

    public void stop() {
        handlerExecutor.shutdown();
        try {
            handlerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            log.info("Stop sequence, handlerExecutor has been interrupted");
        }
    }

    public void dispatch(final M modelDao) {
        log.debug("Dispatching entry {}", modelDao);
        final CallableQueueHandler<E, M> entry = new CallableQueueHandler<E, M>(modelDao, handlerCallback, parentLifeCycle, clock, maxFailureRetries);
        handlerExecutor.submit(entry);
    }

    public static class CallableQueueHandler<E extends QueueEvent, M extends EventEntryModelDao> implements Callable<E> {

        private static final String MDC_KB_USER_TOKEN = "kb.userToken";

        private static final Logger log = LoggerFactory.getLogger(CallableQueueHandler.class);

        private final M entry;
        private final CallableCallback<E, M> callback;
        private final DefaultQueueLifecycle parentLifeCycle;
        private final int maxFailureRetries;
        private final Clock clock;

        public CallableQueueHandler(final M entry, final CallableCallback<E, M> callback, final DefaultQueueLifecycle parentLifeCycle, final Clock clock, final int maxFailureRetries) {
            this.entry = entry;
            this.callback = callback;
            this.parentLifeCycle = parentLifeCycle;
            this.clock = clock;
            this.maxFailureRetries = maxFailureRetries;
        }

        @Override
        public E call() throws Exception {
            try {
                final UUID userToken = entry.getUserToken();
                MDC.put(MDC_KB_USER_TOKEN, userToken != null ? userToken.toString() : null);

                log.debug("Starting processing entry {}", entry);
                final E event = callback.deserialize(entry);
                if (event != null) {
                    Throwable lastException = null;
                    long errorCount = entry.getErrorCount();
                    try {
                        callback.dispatch(event, entry);
                    } catch (final Exception e) {
                        if (e.getCause() != null && e.getCause() instanceof InvocationTargetException) {
                            lastException = e.getCause().getCause();
                        } else if (e.getCause() != null && e.getCause() instanceof RetryableInternalException) {
                            lastException = e.getCause();
                        } else {
                            lastException = e;
                        }
                        errorCount++;
                    } finally {

                        if (parentLifeCycle != null) {
                            if (lastException == null) {
                                final M newEntry = callback.buildEntry(entry, clock.getUTCNow(), PersistentQueueEntryLifecycleState.PROCESSED, entry.getErrorCount());
                                parentLifeCycle.dispatchCompletedOrFailedEvents(newEntry);

                                log.debug("Done handling notification {}, key = {}", entry.getRecordId(), entry.getEventJson());
                            } else if (lastException instanceof RetryableInternalException) {

                                final M newEntry = callback.buildEntry(entry, clock.getUTCNow(), PersistentQueueEntryLifecycleState.FAILED, entry.getErrorCount());
                                parentLifeCycle.dispatchCompletedOrFailedEvents(newEntry);
                            } else if (errorCount <= maxFailureRetries) {

                                log.info("Dispatch error, will attempt a retry ", lastException);

                                final M newEntry = callback.buildEntry(entry, clock.getUTCNow(), PersistentQueueEntryLifecycleState.AVAILABLE, errorCount);
                                parentLifeCycle.dispatchRetriedEvents(newEntry);
                            } else {

                                log.error("Fatal NotificationQ dispatch error, data corruption...", lastException);

                                final M newEntry = callback.buildEntry(entry, clock.getUTCNow(), PersistentQueueEntryLifecycleState.FAILED, entry.getErrorCount());
                                parentLifeCycle.dispatchCompletedOrFailedEvents(newEntry);
                            }
                        }
                    }
                }
                return event;
            } finally {
                MDC.remove(MDC_KB_USER_TOKEN);
            }
        }
    }

}