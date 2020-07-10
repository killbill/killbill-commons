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

package org.killbill.commons.concurrent;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Similar to Java's {@link java.util.concurrent.Executors}, but ensures either a {@link LoggingExecutor} or named {@link FailsafeScheduledExecutor} is used.
 */
@SuppressWarnings("UnusedDeclaration")
public class Executors {

    /*
     * Fixed ThreadPool
     */

    public static ExecutorService newFixedThreadPool(final int nThreads, final String name) {
        return new LoggingExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory(name));
    }

    public static ExecutorService newFixedThreadPool(final int nThreads, final String name, final long keepAliveTime, final TimeUnit unit) {
        return new LoggingExecutor(nThreads, nThreads, keepAliveTime, unit, new NamedThreadFactory(name));
    }

    public static ExecutorService newFixedThreadPool(final int nThreads, final String name, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue) {
        return new LoggingExecutor(nThreads, nThreads, keepAliveTime, unit, workQueue, new NamedThreadFactory(name));
    }

    public static ExecutorService newFixedThreadPool(final int nThreads, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final ThreadFactory threadFactory) {
        return new LoggingExecutor(nThreads, nThreads, keepAliveTime, unit, workQueue, threadFactory);
    }

    public static ExecutorService newFixedThreadPool(final int nThreads, final String name, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final RejectedExecutionHandler handler) {
        return new LoggingExecutor(nThreads, nThreads, keepAliveTime, unit, workQueue, new NamedThreadFactory(name), handler);
    }

    public static ExecutorService newFixedThreadPool(final int nThreads, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final ThreadFactory threadFactory, final RejectedExecutionHandler handler) {
        return new LoggingExecutor(nThreads, nThreads, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    /*
     * Single threaded (fixed ThreadPool of 1)
     */

    public static ExecutorService newSingleThreadExecutor(final String name) {
        return new FinalizableDelegatedExecutorService(newFixedThreadPool(1, name));
    }

    public static ExecutorService newSingleThreadExecutor(final String name, final long keepAliveTime, final TimeUnit unit) {
        return new FinalizableDelegatedExecutorService(newFixedThreadPool(1, name, keepAliveTime, unit));
    }

    public static ExecutorService newSingleThreadExecutor(final String name, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue) {
        return new FinalizableDelegatedExecutorService(newFixedThreadPool(1, name, keepAliveTime, unit, workQueue));
    }

    public static ExecutorService newSingleThreadExecutor(final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final ThreadFactory threadFactory) {
        return new FinalizableDelegatedExecutorService(newFixedThreadPool(1, keepAliveTime, unit, workQueue, threadFactory));
    }

    public static ExecutorService newSingleThreadExecutor(final String name, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final RejectedExecutionHandler handler) {
        return new FinalizableDelegatedExecutorService(newFixedThreadPool(1, name, keepAliveTime, unit, workQueue, handler));
    }

    public static ExecutorService newSingleThreadExecutor(final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final ThreadFactory threadFactory, final RejectedExecutionHandler handler) {
        return new FinalizableDelegatedExecutorService(newFixedThreadPool(1, keepAliveTime, unit, workQueue, threadFactory, handler));
    }

    /*
     * Cached ThreadPool
     */

    public static ExecutorService newCachedThreadPool(final String name) {
        return new LoggingExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new NamedThreadFactory(name));
    }

    public static ExecutorService newCachedThreadPool(final int minThreads, final int maxThreads, final String name) {
        return new LoggingExecutor(minThreads, maxThreads, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new NamedThreadFactory(name));
    }

    public static ExecutorService newCachedThreadPool(final int minThreads, final int maxThreads, final String name, final long keepAliveTime, final TimeUnit unit) {
        return new LoggingExecutor(minThreads, maxThreads, keepAliveTime, unit, new SynchronousQueue<Runnable>(), new NamedThreadFactory(name));
    }

    public static ExecutorService newCachedThreadPool(final int minThreads, final int maxThreads, final String name, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue) {
        return new LoggingExecutor(minThreads, maxThreads, keepAliveTime, unit, workQueue, new NamedThreadFactory(name));
    }

    public static ExecutorService newCachedThreadPool(final int minThreads, final int maxThreads, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final ThreadFactory threadFactory) {
        return new LoggingExecutor(minThreads, maxThreads, keepAliveTime, unit, workQueue, threadFactory);
    }

    public static ExecutorService newCachedThreadPool(final int minThreads, final int maxThreads, final String name, final long keepAliveTime, final TimeUnit unit, final RejectedExecutionHandler handler) {
        return new LoggingExecutor(minThreads, maxThreads, keepAliveTime, unit, new SynchronousQueue<Runnable>(), new NamedThreadFactory(name), handler);
    }

    public static ExecutorService newCachedThreadPool(final int minThreads, final int maxThreads, final String name, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final RejectedExecutionHandler handler) {
        return new LoggingExecutor(minThreads, maxThreads, keepAliveTime, unit, workQueue, new NamedThreadFactory(name), handler);
    }

    public static ExecutorService newCachedThreadPool(final int minThreads, final int maxThreads, final long keepAliveTime, final TimeUnit unit, final BlockingQueue<Runnable> workQueue, final ThreadFactory threadFactory, final RejectedExecutionHandler handler) {
        return new LoggingExecutor(minThreads, maxThreads, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    /*
     * Single threaded scheduled executor
     */

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(final String name) {
        return new DelegatedScheduledExecutorService(new FailsafeScheduledExecutor(name));
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(final ThreadFactory threadFactory) {
        return new DelegatedScheduledExecutorService(new FailsafeScheduledExecutor(1, threadFactory));
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(final String name, final RejectedExecutionHandler handler) {
        return new DelegatedScheduledExecutorService(new FailsafeScheduledExecutor(1, name, handler));
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(final ThreadFactory threadFactory, final RejectedExecutionHandler handler) {
        return new DelegatedScheduledExecutorService(new FailsafeScheduledExecutor(1, threadFactory, handler));
    }

    /*
     * Scheduled ThreadPool
     */

    public static ScheduledExecutorService newScheduledThreadPool(final int corePoolSize, final String name) {
        return new FailsafeScheduledExecutor(corePoolSize, name);
    }

    public static ScheduledExecutorService newScheduledThreadPool(final int corePoolSize, final ThreadFactory threadFactory) {
        return new FailsafeScheduledExecutor(corePoolSize, threadFactory);
    }

    public static ScheduledExecutorService newScheduledThreadPool(final int corePoolSize, final String name, final RejectedExecutionHandler handler) {
        return new FailsafeScheduledExecutor(corePoolSize, name, handler);
    }

    public static ScheduledExecutorService newScheduledThreadPool(final int corePoolSize, final ThreadFactory threadFactory, final RejectedExecutionHandler handler) {
        return new FailsafeScheduledExecutor(corePoolSize, threadFactory, handler);
    }

    private static class DelegatedExecutorService extends AbstractExecutorService {

        private final ExecutorService e;

        DelegatedExecutorService(final ExecutorService executor) {
            e = executor;
        }

        @Override
        public void execute(final Runnable command) {
            e.execute(command);
        }

        @Override
        public void shutdown() {
            e.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return e.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return e.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return e.isTerminated();
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
            return e.awaitTermination(timeout, unit);
        }

        @Override
        public Future<?> submit(final Runnable task) {
            return e.submit(task);
        }

        @Override
        public <T> Future<T> submit(final Callable<T> task) {
            return e.submit(task);
        }

        @Override
        public <T> Future<T> submit(final Runnable task, final T result) {
            return e.submit(task, result);
        }

        @Override
        public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
            return e.invokeAll(tasks);
        }

        @Override
        public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) throws InterruptedException {
            return e.invokeAll(tasks, timeout, unit);
        }

        @Override
        public <T> T invokeAny(final Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            return e.invokeAny(tasks);
        }

        @Override
        public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return e.invokeAny(tasks, timeout, unit);
        }
    }

    private static class FinalizableDelegatedExecutorService extends DelegatedExecutorService {

        FinalizableDelegatedExecutorService(final ExecutorService executor) {
            super(executor);
        }

        @Override
        protected void finalize() throws Throwable {
            super.shutdown();
            super.finalize();
        }
    }

    private static class DelegatedScheduledExecutorService extends DelegatedExecutorService implements ScheduledExecutorService {

        private final ScheduledExecutorService e;

        DelegatedScheduledExecutorService(final ScheduledExecutorService executor) {
            super(executor);
            e = executor;
        }

        @Override
        public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
            return e.schedule(command, delay, unit);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit) {
            return e.schedule(callable, delay, unit);
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay, final long period, final TimeUnit unit) {
            return e.scheduleAtFixedRate(command, initialDelay, period, unit);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, final long initialDelay, final long delay, final TimeUnit unit) {
            return e.scheduleWithFixedDelay(command, initialDelay, delay, unit);
        }
    }

    private Executors() {}
}
