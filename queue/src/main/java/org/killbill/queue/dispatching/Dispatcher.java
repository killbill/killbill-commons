/*
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

package org.killbill.queue.dispatching;

import org.killbill.commons.concurrent.Executors;
import org.killbill.queue.api.QueueEvent;
import org.killbill.queue.dao.EventEntryModelDao;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class Dispatcher<M extends EventEntryModelDao> {


    private final ExecutorService executor;

    public Dispatcher(int corePoolSize,
                      int maximumPoolSize,
                      long keepAliveTime,
                      TimeUnit unit,
                      BlockingQueue<Runnable> workQueue,
                      final ThreadFactory threadFactory,
                      RejectedExecutionHandler rejectionHandler) {
        this.executor = Executors.newCachedThreadPool(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, rejectionHandler);
    }

    public <E extends QueueEvent> void dispatch(final M modelDao, final CallableCallback<E, M> callback) {
        final CallableQueue<E, M> entry = new CallableQueue<E, M>(modelDao, callback);
        executor.submit(entry);
    }

    public static class CallableQueue<E extends QueueEvent, M extends EventEntryModelDao> implements Callable<E> {

        private final M entry;
        private final CallableCallback callback;

        public CallableQueue(final M entry, final CallableCallback callback) {
            this.entry = entry;
            this.callback = callback;
        }

        @Override
        public E call() throws Exception {
            final E event = (E) callback.deserialize(entry);
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
