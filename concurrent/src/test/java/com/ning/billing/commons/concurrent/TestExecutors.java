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

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.WriterAppender;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test(singleThreaded = true)
public class TestExecutors {

    private void registerAppenders(final Logger loggingLogger, final Logger failsafeLogger, final WriterAppender dummyAppender) {
        dummyAppender.setImmediateFlush(true);
        loggingLogger.setLevel(Level.DEBUG);
        failsafeLogger.setLevel(Level.DEBUG);
        loggingLogger.addAppender(dummyAppender);
        failsafeLogger.addAppender(dummyAppender);
    }

    private void unregisterAppenders(final ExecutorService executorService, final Logger loggingLogger, final Logger failsafeLogger, final WriterAppender dummyAppender) throws InterruptedException {
        executorService.shutdown();
        Assert.assertTrue(executorService.isShutdown());
        Assert.assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));
        Assert.assertEquals(executorService.shutdownNow().size(), 0);
        Assert.assertTrue(executorService.isTerminated());
        loggingLogger.removeAppender(dummyAppender);
        failsafeLogger.removeAppender(dummyAppender);
    }

    private void runtimeTest(final ExecutorService executorService) throws Exception {
        final Logger loggingLogger = Logger.getLogger(LoggingExecutor.class);
        final Logger failsafeLogger = Logger.getLogger(FailsafeScheduledExecutor.class);
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final WriterAppender dummyAppender = new WriterAppender(new SimpleLayout(), bos);

        registerAppenders(loggingLogger, failsafeLogger, dummyAppender);

        Future<?> future = executorService.submit(new Runnable() {
            @Override
            public void run() {
                throw new RuntimeException("Fail!");
            }
        });

        Assert.assertNull(future.get());
        future = executorService.submit(new Runnable() {
            @Override
            public void run() {
            }
        }, "bright");
        Assert.assertEquals(future.get(), "bright");
        future = executorService.submit(new Runnable() {
            @Override
            public void run() {
                throw new RuntimeException("Again!");
            }
        }, "Unimportant");

        try {
            future.get();
            Assert.fail("Expected exception");
        } catch (ExecutionException e) {
            Assert.assertEquals(e.getCause().toString(), "java.lang.RuntimeException: Again!");
        }

        unregisterAppenders(executorService, loggingLogger, failsafeLogger, dummyAppender);

        final String actual = bos.toString();

        assertPattern(actual, Pattern.compile("^ERROR - Thread\\[TestLoggingExecutor-[^\\]]+\\] ended abnormally with an exception\njava.lang.RuntimeException: Fail!\n"));
        assertPattern(actual, Pattern.compile("DEBUG - Thread\\[TestLoggingExecutor-[^\\]]+\\] finished executing$"));
    }

    private void errorTest(final ExecutorService executorService) throws Exception {
        final Logger loggingLogger = Logger.getLogger(LoggingExecutor.class);
        final Logger failsafeLogger = Logger.getLogger(FailsafeScheduledExecutor.class);
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final WriterAppender dummyAppender = new WriterAppender(new SimpleLayout(), bos);

        registerAppenders(loggingLogger, failsafeLogger, dummyAppender);
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                throw new OutOfMemoryError("Poof!");
            }
        });
        unregisterAppenders(executorService, loggingLogger, failsafeLogger, dummyAppender);

        final String actual = bos.toString();

        assertPattern(actual, Pattern.compile("^ERROR - Thread\\[TestLoggingExecutor-[^\\]]+\\] ended abnormally with an exception\njava.lang.OutOfMemoryError: Poof!\n"));
        assertPattern(actual, Pattern.compile("DEBUG - Thread\\[TestLoggingExecutor-[^\\]]+\\] finished executing$"));
    }

    private void callableTest(final ExecutorService executorService) throws Exception {
        final Logger loggingLogger = Logger.getLogger(LoggingExecutor.class);
        final Logger failsafeLogger = Logger.getLogger(FailsafeScheduledExecutor.class);
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final WriterAppender dummyAppender = new WriterAppender(new SimpleLayout(), bos);

        registerAppenders(loggingLogger, failsafeLogger, dummyAppender);

        Future<?> future = executorService.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                throw new Exception("Oops!");
            }
        });

        try {
            future.get();
            Assert.fail("Expected exception");
        } catch (ExecutionException e) {
            Assert.assertEquals(e.getCause().toString(), "java.lang.Exception: Oops!");
        }

        future = executorService.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                throw new OutOfMemoryError("Uh oh!");
            }
        });

        try {
            future.get();
            Assert.fail("Expected exception");
        } catch (ExecutionException e) {
            Assert.assertEquals(e.getCause().toString(), "java.lang.OutOfMemoryError: Uh oh!");
        }

        unregisterAppenders(executorService, loggingLogger, failsafeLogger, dummyAppender);

        final String actual = bos.toString();

        assertPattern(actual, Pattern.compile("^DEBUG - Thread\\[TestLoggingExecutor-[^\\]]+\\] ended with an exception\njava.lang.Exception: Oops!\n"));
        assertPattern(actual, Pattern.compile("ERROR - Thread\\[TestLoggingExecutor-[^\\]]+\\] ended with an exception\njava.lang.OutOfMemoryError: Uh oh!\n"));
        assertPattern(actual, Pattern.compile("DEBUG - Thread\\[TestLoggingExecutor-[^\\]]+\\] finished executing$"));
    }

    private void scheduledTest(final ScheduledExecutorService executorService) throws Exception {
        final Logger loggingLogger = Logger.getLogger(LoggingExecutor.class);
        final Logger failsafeLogger = Logger.getLogger(FailsafeScheduledExecutor.class);
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final WriterAppender dummyAppender = new WriterAppender(new SimpleLayout(), bos);

        registerAppenders(loggingLogger, failsafeLogger, dummyAppender);

        final CountDownLatch scheduleLatch = new CountDownLatch(1);
        final CountDownLatch fixedDelayLatch = new CountDownLatch(2);
        final CountDownLatch fixedRateLatch = new CountDownLatch(2);
        final Future<?> future = executorService.schedule(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                throw new Exception("Pow!");
            }
        }, 1, TimeUnit.MILLISECONDS);

        executorService.schedule(new Runnable() {
            @Override
            public void run() {
                scheduleLatch.countDown();

                throw new RuntimeException("D'oh!");
            }
        }, 1, TimeUnit.MILLISECONDS);
        executorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                fixedDelayLatch.countDown();

                if (fixedDelayLatch.getCount() != 0) {
                    throw new OutOfMemoryError("Zoinks!");
                } else {
                    throw new RuntimeException("Eep!");
                }
            }
        }, 1, 1, TimeUnit.MILLISECONDS);
        executorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                fixedRateLatch.countDown();

                if (fixedRateLatch.getCount() != 0) {
                    throw new OutOfMemoryError("Zounds!");
                } else {
                    throw new RuntimeException("Egad!");
                }
            }
        }, 1, 1, TimeUnit.MILLISECONDS);


        try {
            future.get();
            Assert.fail("Expected exception");
        } catch (ExecutionException e) {
            Assert.assertEquals(e.getCause().toString(), "java.lang.Exception: Pow!");
        }

        scheduleLatch.await();
        fixedDelayLatch.await();
        fixedRateLatch.await();
        unregisterAppenders(executorService, loggingLogger, failsafeLogger, dummyAppender);

        final String actual = bos.toString();

        assertPattern(actual, Pattern.compile("ERROR - Thread\\[TestLoggingExecutor-[^\\]]+\\] ended abnormally with an exception\njava.lang.RuntimeException: D'oh!\n"));
        assertPattern(actual, Pattern.compile("ERROR - Thread\\[TestLoggingExecutor-[^\\]]+\\] ended abnormally with an exception\njava.lang.OutOfMemoryError: Zoinks!\n"));
        assertPattern(actual, Pattern.compile("ERROR - Thread\\[TestLoggingExecutor-[^\\]]+\\] ended abnormally with an exception\njava.lang.RuntimeException: Eep!\n"));
        assertPattern(actual, Pattern.compile("ERROR - Thread\\[TestLoggingExecutor-[^\\]]+\\] ended abnormally with an exception\njava.lang.OutOfMemoryError: Zounds!\n"));
        assertPattern(actual, Pattern.compile("ERROR - Thread\\[TestLoggingExecutor-[^\\]]+\\] ended abnormally with an exception\njava.lang.RuntimeException: Egad!\n"));
        assertPattern(actual, Pattern.compile("DEBUG - Thread\\[TestLoggingExecutor-[^\\]]+\\] finished executing$"));
    }

    private void assertPattern(final String actual, final Pattern expected) {
        Assert.assertTrue(expected.matcher(actual).find(), String.format("Expected to see:\n%s\nin:\n%s", indent(expected.toString()), indent(actual)));
    }

    private String indent(final String str) {
        return "\t" + str.replaceAll("\n", "\n\t");
    }

    @Test(groups = "fast")
    public void testSingleThreadExecutorRuntimeException() throws Exception {
        runtimeTest(Executors.newSingleThreadExecutor("TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testSingleThreadExecutorError() throws Exception {
        errorTest(Executors.newSingleThreadExecutor("TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testSingleThreadExecutorCallable() throws Exception {
        callableTest(Executors.newSingleThreadExecutor("TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testCachedThreadPoolRuntimeException() throws Exception {
        runtimeTest(Executors.newCachedThreadPool("TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testCachedThreadPoolError() throws Exception {
        errorTest(Executors.newCachedThreadPool("TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testCachedThreadPoolCallable() throws Exception {
        callableTest(Executors.newCachedThreadPool("TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testFixedThreadPoolRuntimeException() throws Exception {
        runtimeTest(Executors.newFixedThreadPool(10, "TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testFixedThreadPoolError() throws Exception {
        errorTest(Executors.newFixedThreadPool(10, "TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testFixedThreadPoolCallable() throws Exception {
        callableTest(Executors.newFixedThreadPool(10, "TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testScheduledThreadPoolRuntimeException() throws Exception {
        runtimeTest(Executors.newScheduledThreadPool(10, "TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testScheduledThreadPoolError() throws Exception {
        errorTest(Executors.newScheduledThreadPool(10, "TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testScheduledThreadPoolCallable() throws Exception {
        callableTest(Executors.newScheduledThreadPool(10, "TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testScheduledThreadPoolScheduled() throws Exception {
        scheduledTest(Executors.newScheduledThreadPool(10, "TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testSingleThreadScheduledExecutorRuntimeException() throws Exception {
        runtimeTest(Executors.newSingleThreadScheduledExecutor("TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testSingleThreadScheduledExecutorError() throws Exception {
        errorTest(Executors.newSingleThreadScheduledExecutor("TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testSingleThreadScheduledExecutorCallable() throws Exception {
        callableTest(Executors.newSingleThreadScheduledExecutor("TestLoggingExecutor"));
    }

    @Test(groups = "fast")
    public void testSingleThreadScheduledExecutorScheduled() throws Exception {
        scheduledTest(Executors.newSingleThreadScheduledExecutor("TestLoggingExecutor"));
    }
}
