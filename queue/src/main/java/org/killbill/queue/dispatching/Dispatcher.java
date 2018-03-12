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

package org.killbill.queue.dispatching;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.killbill.commons.concurrent.DynamicThreadPoolExecutorWithLoggingOnExceptions;
import org.killbill.queue.api.QueueEvent;
import org.killbill.queue.dao.EventEntryModelDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dispatcher<M extends EventEntryModelDao> {

    private static final Logger log = LoggerFactory.getLogger(Dispatcher.class);

    private final int corePoolSize;
    private final int maximumPoolSize;
    private final long keepAliveTime;
    private final TimeUnit keepAliveTimeUnit;
    private final BlockingQueue<Runnable> workQueue;
    private final ThreadFactory threadFactory;
    private final RejectedExecutionHandler rejectionHandler;

    // Deferred in start sequence to allow for restart, which is not possible after the shutdown (mostly for test purpose)
    private ExecutorService executor;

    public Dispatcher(final int corePoolSize,
                      final int maximumPoolSize,
                      final long keepAliveTime,
                      final TimeUnit keepAliveTimeUnit,
                      final BlockingQueue<Runnable> workQueue,
                      final ThreadFactory threadFactory,
                      final RejectedExecutionHandler rejectionHandler) {
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.keepAliveTimeUnit = keepAliveTimeUnit;
        this.workQueue = workQueue;
        this.threadFactory = threadFactory;
        this.rejectionHandler = rejectionHandler;
    }

    public void start() {
        this.executor = new DynamicThreadPoolExecutorWithLoggingOnExceptions(corePoolSize, maximumPoolSize, keepAliveTime, keepAliveTimeUnit, workQueue, threadFactory, rejectionHandler);
    }

    public void stop() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            log.info(String.format("Stop sequence has been interrupted"));
        }
    }


    public <E extends QueueEvent> void dispatch(final M modelDao, final CallableCallback<E, M> callback) {
        log.debug("Dispatching entry {}", modelDao);
        final CallableQueue<E, M> entry = new CallableQueue<E, M>(modelDao, callback);
        executor.submit(entry);
    }

    public static class CallableQueue<E extends QueueEvent, M extends EventEntryModelDao> implements Callable<E> {

        private final M entry;
        private final CallableCallback<E, M> callback;

        public CallableQueue(final M entry, final CallableCallback<E, M> callback) {
            this.entry = entry;
            this.callback = callback;
        }

        @Override
        public E call() throws Exception {
            final E event = callback.deserialize(entry);
            if (event != null) {
                Throwable lastException = null;
                long errorCount = entry.getErrorCount();
                try {
                    callback.dispatch(event, entry);
                } catch (final Exception e) {
                    if (e.getCause() != null && e.getCause() instanceof InvocationTargetException) {
                        lastException = e.getCause().getCause();
                    } else {
                        lastException = e;
                    }
                    lastException = e;
                    errorCount++;
                } finally {
                    callback.updateErrorCountOrMoveToHistory(event, entry, errorCount, lastException);
                }
            }
            return event;
        }
    }
}
