/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.commons.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension of {@link java.util.concurrent.ScheduledThreadPoolExecutor} that will continue to schedule a task even if the previous run had an exception.
 * Also ensures that uncaught exceptions are logged.
 */
public class FailsafeScheduledExecutor extends ScheduledThreadPoolExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(FailsafeScheduledExecutor.class);

    /**
     * Creates a new single-threaded executor with a {@link NamedThreadFactory} of the given name.
     *
     * @param name thread name base
     */
    public FailsafeScheduledExecutor(final String name) {
        this(1, name);
    }

    /**
     * Creates a new executor with a {@link NamedThreadFactory} of the given name.
     *
     * @param corePoolSize number of threads in the pool
     * @param name         thread name base
     */
    public FailsafeScheduledExecutor(final int corePoolSize, final String name) {
        this(corePoolSize, new NamedThreadFactory(name));
    }

    /**
     * Creates a new executor with the given thread factory.
     *
     * @param corePoolSize  number of threads in the pool
     * @param threadFactory a thread factory to use
     */
    public FailsafeScheduledExecutor(final int corePoolSize, final ThreadFactory threadFactory) {
        super(corePoolSize, threadFactory);
    }

    /**
     * Creates a new executor with the given thread factory.
     *
     * @param corePoolSize number of threads in the pool
     * @param name         thread name base
     * @param handler      a rejected execution handler to use
     */
    public FailsafeScheduledExecutor(final int corePoolSize, final String name, final RejectedExecutionHandler handler) {
        super(corePoolSize, new NamedThreadFactory(name), handler);
    }

    /**
     * Creates a new executor with the given thread factory.
     *
     * @param corePoolSize  number of threads in the pool
     * @param threadFactory a thread factory to use
     * @param handler       a rejected execution handler to use
     */
    public FailsafeScheduledExecutor(final int corePoolSize, final ThreadFactory threadFactory, final RejectedExecutionHandler handler) {
        super(corePoolSize, threadFactory, handler);
    }

    @Override
    public <T> Future<T> submit(final Callable<T> task) {
        return super.submit(WrappedCallable.wrap(LOG, task));
    }

    @Override
    public <T> Future<T> submit(final Runnable task, final T result) {
        final WrappedRunnable runnable = WrappedRunnable.wrap(LOG, task);
        final Future<T> future = super.submit(runnable, result);

        return WrappedRunnableFuture.wrap(runnable, future);
    }

    @Override
    public Future<?> submit(final Runnable task) {
        final WrappedRunnable runnable = WrappedRunnable.wrap(LOG, task);
        final Future<?> future = super.submit(runnable);

        return WrappedRunnableFuture.wrap(runnable, future);
    }

    @Override
    public void execute(final Runnable command) {
        super.execute(WrappedRunnable.wrap(LOG, command));
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, final long initialDelay, final long delay, final TimeUnit unit) {
        return super.scheduleWithFixedDelay(WrappedRunnable.wrap(LOG, command), initialDelay, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay, final long period, final TimeUnit unit) {
        return super.scheduleAtFixedRate(WrappedRunnable.wrap(LOG, command), initialDelay, period, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit) {
        return super.schedule(WrappedCallable.wrap(LOG, callable), delay, unit);
    }

    @Override
    public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
        return super.schedule(WrappedRunnable.wrap(LOG, command), delay, unit);
    }
}
