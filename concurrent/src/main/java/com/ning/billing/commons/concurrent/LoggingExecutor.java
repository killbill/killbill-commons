/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.commons.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension of {@link java.util.concurrent.ThreadPoolExecutor} that ensures any uncaught exceptions are logged.
 */
public class LoggingExecutor extends ThreadPoolExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingExecutor.class);

    public LoggingExecutor(final int corePoolSize, final int maximumPoolSize, final String name, final long keepAliveTime, final TimeUnit unit) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory(name));
    }

    public LoggingExecutor(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<Runnable>(), threadFactory);
    }

    public LoggingExecutor(final int corePoolSize, final int maximumPoolSize, final String name, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, new NamedThreadFactory(name));
    }

    public LoggingExecutor(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public LoggingExecutor(final int corePoolSize, final int maximumPoolSize, final String name, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, new NamedThreadFactory(name), handler);
    }

    public LoggingExecutor(final int corePoolSize, final int maximumPoolSize, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final ThreadFactory threadFactory, final RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    @Override
    public <T> Future<T> submit(final Callable<T> task) {
        return super.submit(WrappedCallable.wrap(LOG, task));
    }

    @Override
    public <T> Future<T> submit(final Runnable task, final T result) {
        // HACK: assumes ThreadPoolExecutor will create a callable and call execute()
        // (can't wrap the runnable here or exception isn't re-thrown when Future.get() is called)
        return super.submit(task, result);
    }

    @Override
    public Future<?> submit(final Runnable task) {
        return super.submit(WrappedRunnable.wrap(LOG, task));
    }

    @Override
    public void execute(final Runnable command) {
        super.execute(WrappedRunnable.wrap(LOG, command));
    }
}
