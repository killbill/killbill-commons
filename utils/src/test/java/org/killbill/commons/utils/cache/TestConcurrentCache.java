/*
 * Copyright 2026 The Billing Project, LLC
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

package org.killbill.commons.utils.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestConcurrentCache {

    // --- Constructor validation ---

    @Test(groups = "fast")
    public void testConstructorRejectsNegativeMaxSize() {
        try {
            new ConcurrentCache<String, String>(-1);
            Assert.fail("Should reject negative maxSize");
        } catch (final IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), "cache maxSize should >= 0");
        }
    }

    @Test(groups = "fast")
    public void testConstructorRejectsNegativeTimeout() {
        try {
            new ConcurrentCache<String, String>(10, -1, null);
            Assert.fail("Should reject negative timeout");
        } catch (final IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), "cache timeoutInSecond should >= 0");
        }
    }

    @Test(groups = "fast")
    public void testConstructorRejectsZeroConcurrencyLevel() {
        try {
            new ConcurrentCache<String, String>(10, 0, 0, null);
            Assert.fail("Should reject zero concurrencyLevel");
        } catch (final IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), "cache concurrencyLevel should > 0");
        }
    }

    @Test(groups = "fast")
    public void testConstructorAcceptsMaxSizeZero() {
        // maxSize=0 is valid (means "no caching")
        final ConcurrentCache<String, String> cache = new ConcurrentCache<>(0);
        Assert.assertNotNull(cache);
    }

    // --- Basic put/get/invalidate ---

    @Test(groups = "fast")
    public void testPutAndGet() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10);

        cache.put(1, "A");
        cache.put(2, "B");
        cache.put(3, "C");

        Assert.assertEquals(cache.get(1), "A");
        Assert.assertEquals(cache.get(2), "B");
        Assert.assertEquals(cache.get(3), "C");
    }

    @Test(groups = "fast")
    public void testGetReturnsNullForMissingKey() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10);
        Assert.assertNull(cache.get(99));
    }

    @Test(groups = "fast")
    public void testPutOverwritesExistingValue() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10);

        cache.put(1, "A");
        Assert.assertEquals(cache.get(1), "A");

        cache.put(1, "B");
        Assert.assertEquals(cache.get(1), "B");
    }

    @Test(groups = "fast")
    public void testInvalidate() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10);

        cache.put(1, "A");
        cache.put(2, "B");

        cache.invalidate(1);

        Assert.assertNull(cache.get(1));
        Assert.assertEquals(cache.get(2), "B");
        Assert.assertEquals(cache.map.size(), 1);
    }

    // --- Null key/value handling ---

    @Test(groups = "fast")
    public void testGetNullKeyThrows() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10);
        try {
            cache.get(null);
            Assert.fail("Should throw NPE for null key");
        } catch (final NullPointerException e) {
            Assert.assertEquals(e.getMessage(), "Cannot #get() cache with key = null");
        }
    }

    @Test(groups = "fast")
    public void testPutNullKeyThrows() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10);
        try {
            cache.put(null, "A");
            Assert.fail("Should throw NPE for null key");
        } catch (final NullPointerException e) {
            Assert.assertEquals(e.getMessage(), "key in #put() is null");
        }
    }

    @Test(groups = "fast")
    public void testPutNullValueThrows() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10);
        try {
            cache.put(1, null);
            Assert.fail("Should throw NPE for null value");
        } catch (final NullPointerException e) {
            Assert.assertEquals(e.getMessage(), "value in #put() is null");
        }
    }

    @Test(groups = "fast")
    public void testInvalidateNullKeyThrows() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10);
        try {
            cache.invalidate(null);
            Assert.fail("Should throw NPE for null key");
        } catch (final NullPointerException e) {
            Assert.assertEquals(e.getMessage(), "Cannot invalidate. Cache with null key is not allowed");
        }
    }

    @Test(groups = "fast")
    public void testGetOrLoadNullLoaderThrows() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10);
        try {
            cache.getOrLoad(1, null);
            Assert.fail("Should throw NPE for null loader");
        } catch (final NullPointerException e) {
            Assert.assertEquals(e.getMessage(), "loader parameter in #getOrLoad() is null");
        }
    }

    // --- Auto-loading with cacheLoader ---

    @Test(groups = "fast")
    public void testGetAutoLoadsAndStores() {
        final AtomicInteger loadCount = new AtomicInteger(0);
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(loadCount.get(), 1);

        // Second get should serve from cache, not call loader again
        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(loadCount.get(), 1);
    }

    @Test(groups = "fast")
    public void testGetLoaderReturningNullDoesNotStore() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(key -> null);

        Assert.assertNull(cache.get(1));
        Assert.assertEquals(cache.map.size(), 0);
    }

    @Test(groups = "fast")
    public void testGetWithoutLoaderReturnsNull() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10);
        Assert.assertNull(cache.get(42));
    }

    // --- getOrLoad ---

    @Test(groups = "fast")
    public void testGetOrLoadReturnsCachedValue() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10);
        cache.put(1, "A");

        final String result = cache.getOrLoad(1, key -> "should-not-be-called");
        Assert.assertEquals(result, "A");
    }

    @Test(groups = "fast")
    public void testGetOrLoadCallsLoaderOnMiss() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10);

        final String result = cache.getOrLoad(1, key -> "fallback-" + key);
        Assert.assertEquals(result, "fallback-1");
    }

    @Test(groups = "fast")
    public void testGetOrLoadStoresLoaderResult() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10);

        cache.getOrLoad(1, key -> "loaded-" + key);
        // The loader result IS stored in the cache
        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(cache.map.size(), 1);
    }

    @Test(groups = "fast")
    public void testGetOrLoadIgnoresConstructorLoader() {
        final AtomicInteger constructorLoaderCalls = new AtomicInteger(0);
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(
                Integer.MAX_VALUE, ConcurrentCache.NO_TIMEOUT, key -> {
            constructorLoaderCalls.incrementAndGet();
            return "from-constructor-" + key;
        });

        // getOrLoad should use the parameter loader, not the constructor loader
        final String result = cache.getOrLoad(1, key -> "from-param-" + key);
        Assert.assertEquals(result, "from-param-1");
        Assert.assertEquals(constructorLoaderCalls.get(), 0);

        // Value stored by parameter loader
        Assert.assertEquals(cache.get(1), "from-param-1");
    }

    @Test(groups = "fast")
    public void testGetOrLoadDoesNotStoreNullResult() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10);

        final String result = cache.getOrLoad(1, key -> null);
        Assert.assertNull(result);
        Assert.assertEquals(cache.map.size(), 0);
    }

    // --- MaxSize / LRU eviction ---

    @Test(groups = "fast")
    public void testMaxSizeEvictsLeastRecentlyAccessed() throws InterruptedException {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(3);

        cache.put(1, "A");
        Thread.sleep(5);
        cache.put(2, "B");
        Thread.sleep(5);
        cache.put(3, "C");
        Thread.sleep(5);

        // Access key 1 to make it "recently used"
        cache.get(1);
        Thread.sleep(5);

        // Adding key 4 should evict key 2 (least recently accessed)
        cache.put(4, "D");

        Assert.assertNotNull(cache.get(1)); // recently accessed
        Assert.assertNull(cache.get(2));    // evicted (least recently accessed)
        Assert.assertNotNull(cache.get(3));
        Assert.assertNotNull(cache.get(4));
        Assert.assertEquals(cache.map.size(), 3);
    }

    @Test(groups = "fast")
    public void testMaxSizeEvictsMultipleIfNeeded() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(2);

        cache.put(1, "A");
        cache.put(2, "B");
        cache.put(3, "C"); // evicts 1

        Assert.assertEquals(cache.map.size(), 2);
        Assert.assertNull(cache.get(1));
        Assert.assertNotNull(cache.get(2));
        Assert.assertNotNull(cache.get(3));
    }

    // --- MaxSize = 0 (no caching) ---

    @Test(groups = "fast")
    public void testMaxSizeZeroNeverStores() {
        final AtomicInteger loadCount = new AtomicInteger(0);
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(
                0, ConcurrentCache.NO_TIMEOUT, key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        // Loader is called, value is returned, but not stored
        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(cache.map.size(), 0);

        // Loader is called again (not cached)
        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(loadCount.get(), 2);
    }

    @Test(groups = "fast")
    public void testMaxSizeZeroPutIsNoOp() {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(0);

        cache.put(1, "A");
        Assert.assertEquals(cache.map.size(), 0);
        Assert.assertNull(cache.get(1));
    }

    // --- TTL expiration ---

    @Test(groups = "fast")
    public void testExpiredEntryEvictedOnGet() throws InterruptedException {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10, 1, null);

        cache.put(1, "A");
        Assert.assertEquals(cache.get(1), "A");

        Thread.sleep(1100);

        // Entry expired — get should return null and remove it
        Assert.assertNull(cache.get(1));
        Assert.assertEquals(cache.map.size(), 0);
    }

    @Test(groups = "fast")
    public void testExpiredEntryReloadedByCacheLoader() throws InterruptedException {
        final AtomicInteger loadCount = new AtomicInteger(0);
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(
                10, 1, key -> {
            loadCount.incrementAndGet();
            return "reloaded-" + key;
        });

        cache.put(1, "A");
        Assert.assertEquals(cache.get(1), "A");
        Assert.assertEquals(loadCount.get(), 0);

        Thread.sleep(1100);

        // Expired — loader kicks in
        Assert.assertEquals(cache.get(1), "reloaded-1");
        Assert.assertEquals(loadCount.get(), 1);
    }

    @Test(groups = "fast")
    public void testNonExpiredEntryNotEvicted() throws InterruptedException {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(10, 5, null);

        cache.put(1, "A");
        Thread.sleep(100);

        Assert.assertEquals(cache.get(1), "A"); // Not expired (5s TTL)
    }

    // --- concurrencyLevel ---

    @Test(groups = "fast")
    public void testConcurrencyLevelAccepted() {
        // Just verifies construction doesn't throw
        final ConcurrentCache<String, String> cache = new ConcurrentCache<>(100, 0, 8, null);
        cache.put("key", "value");
        Assert.assertEquals(cache.get("key"), "value");
    }

    // --- Thread safety (basic) ---

    @Test(groups = "fast")
    public void testConcurrentPutAndGet() throws InterruptedException {
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(1000);
        final int threadCount = 10;
        final int opsPerThread = 100;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int t = 0; t < threadCount; t++) {
            final int offset = t * opsPerThread;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        cache.put(offset + i, "val-" + (offset + i));
                    }
                    for (int i = 0; i < opsPerThread; i++) {
                        final String val = cache.get(offset + i);
                        if (val == null || !val.equals("val-" + (offset + i))) {
                            errors.add(new AssertionError(
                                    "Expected val-" + (offset + i) + " but got " + val));
                        }
                    }
                } catch (final Throwable e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        if (!errors.isEmpty()) {
            Assert.fail("Concurrent errors: " + errors.get(0).getMessage());
        }
        Assert.assertEquals(cache.map.size(), threadCount * opsPerThread);
    }

    @Test(groups = "fast")
    public void testConcurrentLoadWithCacheLoader() throws InterruptedException {
        final AtomicInteger loadCount = new AtomicInteger(0);
        final ConcurrentCache<Integer, String> cache = new ConcurrentCache<>(key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        final int threadCount = 10;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // All threads request the same key
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    final String val = cache.get(1);
                    Assert.assertEquals(val, "loaded-1");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Value should be in cache
        Assert.assertEquals(cache.get(1), "loaded-1");
    }
}
