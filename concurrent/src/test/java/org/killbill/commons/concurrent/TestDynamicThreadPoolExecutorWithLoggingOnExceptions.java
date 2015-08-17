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

package org.killbill.commons.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TestDynamicThreadPoolExecutorWithLoggingOnExceptions {

    private static final Logger log = LoggerFactory.getLogger(TestDynamicThreadPoolExecutorWithLoggingOnExceptions.class);

    private BlockingQueue<Runnable> queue;
    private ThreadPoolExecutor executor;


    @BeforeMethod(groups = "fast")
    public void beforeMethod() {

        final ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(new ThreadGroup("TestThreadGroup"),
                        r, "-test");

            }
        };
        final RejectedExecutionHandler rejectedExecutionHandler = new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {

            }
        };

        queue = new LinkedBlockingQueue<Runnable>();
        executor = new DynamicThreadPoolExecutorWithLoggingOnExceptions(1, 3, 1, TimeUnit.MINUTES, queue, threadFactory, rejectedExecutionHandler);
        //executor = new ThreadPoolExecutor(1, 3, 1, TimeUnit.MINUTES, queue, threadFactory, rejectedExecutionHandler);
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() {

    }


    @Test(groups = "fast")
    public void testPoolWitMaximumPoolSize() throws InterruptedException {

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(4);

        // Should be handled by corePoolSize ( = 1) thread
        executor.submit(new TestCallable(startSignal, doneSignal));
        Assert.assertEquals(queue.size(), 0);

        // Should spawn two new threads up to maximumPoolSize (= 3)
        executor.submit(new TestCallable(startSignal, doneSignal));
        Assert.assertEquals(queue.size(), 0);

        executor.submit(new TestCallable(startSignal, doneSignal));
        Assert.assertEquals(queue.size(), 0);

        // Submit a new task, queue should grow to 1 as other threads are busy.
        executor.submit(new TestCallable(startSignal, doneSignal));
        Assert.assertEquals(queue.size(), 1);

        // Release other threads
        startSignal.countDown();

        // Wait for all threads to complete
        doneSignal.await();
        Assert.assertEquals(queue.size(), 0);

    }

    public static class TestCallable implements Callable {

        private final CountDownLatch startSignal;
        private final CountDownLatch doneSignal;

        public TestCallable(final CountDownLatch startSignal, final CountDownLatch doneSignal) {
            this.startSignal = startSignal;
            this.doneSignal = doneSignal;
        }

        @Override
        public Object call() throws Exception {
            log.info("Thread " + Thread.currentThread().getId() + " initialized");
            startSignal.await();
            log.info("Thread " + Thread.currentThread().getId() + " is starting");
            doneSignal.countDown();
            log.info("Thread " + Thread.currentThread().getId() + " done!");
            return null;
        }
    }
}
