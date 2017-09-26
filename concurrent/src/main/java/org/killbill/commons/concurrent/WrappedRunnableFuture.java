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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WrappedRunnableFuture<V> implements Future<V> {

    private final WrappedRunnable runnable;
    private final Future<V> delegate;

    private WrappedRunnableFuture(final WrappedRunnable runnable, final Future<V> delegate) {
        this.runnable = runnable;
        this.delegate = delegate;
    }

    public static <V> Future<V> wrap(final WrappedRunnable runnable, final Future<V> delegate) {
        return new WrappedRunnableFuture<V>(runnable, delegate);
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        return delegate.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        final V result = delegate.get();

        checkForException();

        return result;
    }

    @Override
    public V get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        final V result = delegate.get(timeout, unit);

        checkForException();

        return result;
    }

    private void checkForException() throws InterruptedException, ExecutionException {
        final Throwable exception = runnable.getException();

        if (exception != null) {
            if (exception instanceof InterruptedException) {
                throw (InterruptedException) exception;
            }

            if (exception instanceof ExecutionException) {
                throw (ExecutionException) exception;
            }

            throw new ExecutionException(exception);
        }
    }
}
