/*
 * Copyright (C) 2010 The Guava Authors
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.commons.utils.concurrent;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.CheckForNull;

import org.killbill.commons.utils.Preconditions;

import static java.util.Objects.requireNonNull;

/**
 * Verbatim copy of Guava's {@code ThreadFactoryBuilder}. Placed here instead of in {@code concurrent} module to avoid
 * circular dependency.
 *
 * A ThreadFactory builder, providing any combination of these features:
 *
 * <ul>
 *   <li>whether threads should be marked as {@linkplain Thread#setDaemon daemon} threads
 *   <li>a {@linkplain ThreadFactoryBuilder#setNameFormat naming format}
 *   <li>a {@linkplain Thread#setPriority thread priority}
 *   <li>an {@linkplain Thread#setUncaughtExceptionHandler uncaught exception handler}
 *   <li>a {@linkplain ThreadFactory#newThread backing thread factory}
 * </ul>
 *
 * <p>If no backing thread factory is provided, a default backing thread factory is used as if by
 * calling {@code setThreadFactory(}{@link Executors#defaultThreadFactory()}{@code )}.
 *
 * @author Kurt Alfred Kluever
 * @since 4.0
 */
public final class ThreadFactoryBuilder {
    @CheckForNull
    private String nameFormat = null;
    @CheckForNull private Boolean daemon = null;
    @CheckForNull private Integer priority = null;
    @CheckForNull private UncaughtExceptionHandler uncaughtExceptionHandler = null;
    @CheckForNull private ThreadFactory backingThreadFactory = null;

    /** Creates a new {@link ThreadFactory} builder. */
    public ThreadFactoryBuilder() {}

    /**
     * Sets the naming format to use when naming threads ({@link Thread#setName}) which are created
     * with this ThreadFactory.
     *
     * @param nameFormat a {@link String#format(String, Object...)}-compatible format String, to which
     *     a unique integer (0, 1, etc.) will be supplied as the single parameter. This integer will
     *     be unique to the built instance of the ThreadFactory and will be assigned sequentially. For
     *     example, {@code "rpc-pool-%d"} will generate thread names like {@code "rpc-pool-0"}, {@code
     *     "rpc-pool-1"}, {@code "rpc-pool-2"}, etc.
     * @return this for the builder pattern
     */
    public ThreadFactoryBuilder setNameFormat(final String nameFormat) {
        this.nameFormat = nameFormat;
        return this;
    }

    /**
     * Sets daemon or not for new threads created with this ThreadFactory.
     *
     * @param daemon whether or not new Threads created with this ThreadFactory will be daemon threads
     * @return this for the builder pattern
     */
    public ThreadFactoryBuilder setDaemon(final boolean daemon) {
        this.daemon = daemon;
        return this;
    }

    /**
     * Sets the priority for new threads created with this ThreadFactory.
     *
     * @param priority the priority for new Threads created with this ThreadFactory
     * @return this for the builder pattern
     */
    public ThreadFactoryBuilder setPriority(final int priority) {
        // Thread#setPriority() already checks for validity. These error messages
        // are nicer though and will fail-fast.
        Preconditions.checkArgument(priority >= Thread.MIN_PRIORITY,
                                    "Thread priority (%s) must be >= %s",
                                    priority,
                                    Thread.MIN_PRIORITY);
        Preconditions.checkArgument(priority <= Thread.MAX_PRIORITY,
                                    "Thread priority (%s) must be <= %s",
                                    priority,
                                    Thread.MAX_PRIORITY);
        this.priority = priority;
        return this;
    }

    /**
     * Sets the {@link UncaughtExceptionHandler} for new threads created with this ThreadFactory.
     *
     * @param uncaughtExceptionHandler the uncaught exception handler for new Threads created with
     *     this ThreadFactory
     * @return this for the builder pattern
     */
    public ThreadFactoryBuilder setUncaughtExceptionHandler(final UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = Preconditions.checkNotNull(uncaughtExceptionHandler);
        return this;
    }

    /**
     * Sets the backing {@link ThreadFactory} for new threads created with this ThreadFactory. Threads
     * will be created by invoking #newThread(Runnable) on this backing {@link ThreadFactory}.
     *
     * @param backingThreadFactory the backing {@link ThreadFactory} which will be delegated to during
     *     thread creation.
     * @return this for the builder pattern
     */
    public ThreadFactoryBuilder setThreadFactory(final ThreadFactory backingThreadFactory) {
        this.backingThreadFactory = Preconditions.checkNotNull(backingThreadFactory);
        return this;
    }

    /**
     * Returns a new thread factory using the options supplied during the building process. After
     * building, it is still possible to change the options used to build the ThreadFactory and/or
     * build again. State is not shared amongst built instances.
     *
     * @return the fully constructed {@link ThreadFactory}
     */
    public ThreadFactory build() {
        return doBuild(this);
    }

    // Split out so that the anonymous ThreadFactory can't contain a reference back to the builder.
    // At least, I assume that's why. TODO(cpovirk): Check, and maybe add a test for this.
    private static ThreadFactory doBuild(final ThreadFactoryBuilder builder) {
        final String nameFormat = builder.nameFormat;
        final Boolean daemon = builder.daemon;
        final Integer priority = builder.priority;
        final UncaughtExceptionHandler uncaughtExceptionHandler = builder.uncaughtExceptionHandler;
        final ThreadFactory backingThreadFactory = builder.backingThreadFactory != null ?
                                                   builder.backingThreadFactory :
                                                   Executors.defaultThreadFactory();
        final AtomicLong count = (nameFormat != null) ? new AtomicLong(0) : null;
        return new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable runnable) {
                final Thread thread = backingThreadFactory.newThread(runnable);
                if (nameFormat != null) {
                    // requireNonNull is safe because we create `count` if (and only if) we have a nameFormat.
                    thread.setName(format(nameFormat, requireNonNull(count).getAndIncrement()));
                }
                if (daemon != null) {
                    thread.setDaemon(daemon);
                }
                if (priority != null) {
                    thread.setPriority(priority);
                }
                if (uncaughtExceptionHandler != null) {
                    thread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
                }
                return thread;
            }
        };
    }

    private static String format(final String format, final Object... args) {
        return String.format(Locale.ROOT, format, args);
    }
}
