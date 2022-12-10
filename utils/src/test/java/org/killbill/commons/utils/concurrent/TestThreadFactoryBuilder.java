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

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestThreadFactoryBuilder {

    private final Runnable monitoredRunnable = () -> completed = true;

    private static final UncaughtExceptionHandler UNCAUGHT_EXCEPTION_HANDLER =
            new UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(final Thread t, final Throwable e) {
                    // No-op
                }
            };

    private ThreadFactoryBuilder builder;
    private volatile boolean completed = false;

    @BeforeMethod(groups = "fast")
    public void setUp() {
        builder = new ThreadFactoryBuilder();
        completed = false;
    }

    @Test(groups = "fast")
    public void testThreadFactoryBuilder_defaults() throws InterruptedException {
        final ThreadFactory threadFactory = builder.build();
        final Thread thread = threadFactory.newThread(monitoredRunnable);
        checkThreadPoolName(thread, 1);

        final Thread defaultThread = Executors.defaultThreadFactory().newThread(monitoredRunnable);
        Assert.assertEquals(defaultThread.isDaemon(), thread.isDaemon());
        Assert.assertEquals(defaultThread.getPriority(), thread.getPriority());
        Assert.assertSame(defaultThread.getThreadGroup(), thread.getThreadGroup());
        Assert.assertSame(defaultThread.getUncaughtExceptionHandler(), thread.getUncaughtExceptionHandler());

        Assert.assertFalse(completed);
        thread.start();
        thread.join();
        Assert.assertTrue(completed);

        // Creating a new thread from the same ThreadFactory will have the same
        // pool ID but a thread ID of 2.
        final Thread thread2 = threadFactory.newThread(monitoredRunnable);
        checkThreadPoolName(thread2, 2);
        Assert.assertEquals(
                thread.getName().substring(0, thread.getName().lastIndexOf('-')),
                thread2.getName().substring(0, thread.getName().lastIndexOf('-')));

        // Building again should give us a different pool ID.
        final ThreadFactory threadFactory2 = builder.build();
        final Thread thread3 = threadFactory2.newThread(monitoredRunnable);
        checkThreadPoolName(thread3, 1);
        Assert.assertNotEquals(thread2.getName().substring(0, thread.getName().lastIndexOf('-')),
                               thread3.getName().substring(0, thread.getName().lastIndexOf('-')));
    }

    private static void checkThreadPoolName(final Thread thread, final int threadId) {
        Assert.assertTrue(thread.getName().matches("^pool-\\d+-thread-" + threadId + "$"));
    }

    @Test(groups = "fast")
    public void testNameFormatWithPercentS_custom() {
        final String format = "super-duper-thread-%s";
        final ThreadFactory factory = builder.setNameFormat(format).build();
        for (int i = 0; i < 11; i++) {
            Assert.assertEquals(rootLocaleFormat(format, i), factory.newThread(monitoredRunnable).getName());
        }
    }

    @Test(groups = "fast")
    public void testNameFormatWithPercentD_custom() {
        final String format = "super-duper-thread-%d";
        final ThreadFactory factory = builder.setNameFormat(format).build();
        for (int i = 0; i < 11; i++) {
            Assert.assertEquals(rootLocaleFormat(format, i), factory.newThread(monitoredRunnable).getName());
        }
    }

    @Test(groups = "fast")
    public void testDaemon_false() {
        final ThreadFactory factory = builder.setDaemon(false).build();
        final Thread thread = factory.newThread(monitoredRunnable);
        Assert.assertFalse(thread.isDaemon());
    }

    @Test(groups = "fast")
    public void testDaemon_true() {
        final ThreadFactory factory = builder.setDaemon(true).build();
        final Thread thread = factory.newThread(monitoredRunnable);
        Assert.assertTrue(thread.isDaemon());
    }

    @Test(groups = "fast")
    public void testPriority_custom() {
        for (int i = Thread.MIN_PRIORITY; i <= Thread.MAX_PRIORITY; i++) {
            final ThreadFactory factory = builder.setPriority(i).build();
            final Thread thread = factory.newThread(monitoredRunnable);
            Assert.assertEquals(i, thread.getPriority());
        }
    }

    @Test(groups = "fast")
    public void testPriority_tooLow() {
        try {
            builder.setPriority(Thread.MIN_PRIORITY - 1);
            Assert.fail();
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test(groups = "fast")
    public void testPriority_tooHigh() {
        try {
            builder.setPriority(Thread.MAX_PRIORITY + 1);
            Assert.fail();
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test(groups = "fast")
    public void testUncaughtExceptionHandler_custom() {
        Assert.assertEquals(
                UNCAUGHT_EXCEPTION_HANDLER,
                builder
                        .setUncaughtExceptionHandler(UNCAUGHT_EXCEPTION_HANDLER)
                        .build()
                        .newThread(monitoredRunnable)
                        .getUncaughtExceptionHandler());
    }

    @Test(groups = "fast")
    public void testBuildMutateBuild() {
        final ThreadFactory factory1 = builder.setPriority(1).build();
        Assert.assertEquals(1, factory1.newThread(monitoredRunnable).getPriority());

        final ThreadFactory factory2 = builder.setPriority(2).build();
        Assert.assertEquals(1, factory1.newThread(monitoredRunnable).getPriority());
        Assert.assertEquals(2, factory2.newThread(monitoredRunnable).getPriority());
    }

    @Test(groups = "fast")
    public void testBuildTwice() {
        ThreadFactory unused;
        unused = builder.build(); // this is allowed
        unused = builder.build(); // this is *also* allowed
    }

    @Test(groups = "fast")
    public void testBuildMutate() {
        final ThreadFactory factory1 = builder.setPriority(1).build();
        Assert.assertEquals(1, factory1.newThread(monitoredRunnable).getPriority());

        builder.setPriority(2); // change the state of the builder
        Assert.assertEquals(1, factory1.newThread(monitoredRunnable).getPriority());
    }

    @Test(groups = "fast")
    public void testThreadFactory() throws InterruptedException {
        final String THREAD_NAME = "ludicrous speed";
        final int THREAD_PRIORITY = 1;
        final boolean THREAD_DAEMON = false;
        final ThreadFactory backingThreadFactory =
                new ThreadFactory() {
                    @Override
                    public Thread newThread(final Runnable r) {
                        final Thread thread = new Thread(r);
                        thread.setName(THREAD_NAME);
                        thread.setPriority(THREAD_PRIORITY);
                        thread.setDaemon(THREAD_DAEMON);
                        thread.setUncaughtExceptionHandler(UNCAUGHT_EXCEPTION_HANDLER);
                        return thread;
                    }
                };

        final Thread thread = builder.setThreadFactory(backingThreadFactory).build().newThread(monitoredRunnable);

        Assert.assertEquals(THREAD_NAME, thread.getName());
        Assert.assertEquals(THREAD_PRIORITY, thread.getPriority());
        Assert.assertEquals(THREAD_DAEMON, thread.isDaemon());
        Assert.assertSame(UNCAUGHT_EXCEPTION_HANDLER, thread.getUncaughtExceptionHandler());
        Assert.assertSame(Thread.State.NEW, thread.getState());

        Assert.assertFalse(completed);
        thread.start();
        thread.join();
        Assert.assertTrue(completed);
    }

    private static String rootLocaleFormat(final String format, final Object... args) {
        return String.format(Locale.ROOT, format, args);
    }
}
