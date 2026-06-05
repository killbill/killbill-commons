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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestCaffeineCache {

    @Test(groups = "fast")
    public void testGetReturnsNullWithoutLoader() {
        final Cache<String, String> cache = new CaffeineCache<>(null, null, null);

        Assert.assertNull(cache.get("missing"));
    }

    @Test(groups = "fast")
    public void testPutGetAndInvalidate() {
        final Cache<String, String> cache = new CaffeineCache<>(null, null, null);

        cache.put("key", "value");
        Assert.assertEquals(cache.get("key"), "value");

        cache.invalidate("key");
        Assert.assertNull(cache.get("key"));
    }

    @Test(groups = "fast")
    public void testConfiguredLoaderCachesValue() {
        final AtomicInteger loadCount = new AtomicInteger();
        final Cache<Integer, String> cache = new CaffeineCache<>(null, null, key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(loadCount.get(), 1);
    }

    @Test(groups = "fast")
    public void testGetOrLoadCachesValue() {
        final AtomicInteger loadCount = new AtomicInteger();
        final Cache<Integer, String> cache = new CaffeineCache<>(null, null, null);

        Assert.assertEquals(cache.getOrLoad(1, key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        }), "loaded-1");
        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(loadCount.get(), 1);
    }

    @Test(groups = "fast")
    public void testGetOrLoadUsesSuppliedLoaderOnMiss() {
        final AtomicInteger configuredLoaderCalls = new AtomicInteger();
        final AtomicInteger suppliedLoaderCalls = new AtomicInteger();
        final Cache<Integer, String> cache = new CaffeineCache<>(null, null, key -> {
            configuredLoaderCalls.incrementAndGet();
            return "configured-" + key;
        });

        Assert.assertEquals(cache.getOrLoad(1, key -> {
            suppliedLoaderCalls.incrementAndGet();
            return "supplied-" + key;
        }), "supplied-1");
        Assert.assertEquals(configuredLoaderCalls.get(), 0);
        Assert.assertEquals(suppliedLoaderCalls.get(), 1);
    }

    @Test(groups = "fast")
    public void testGetOrLoadReturnsCachedValue() {
        final AtomicInteger suppliedLoaderCalls = new AtomicInteger();
        final Cache<Integer, String> cache = new CaffeineCache<>(null, null, key -> "configured-" + key);

        Assert.assertEquals(cache.get(1), "configured-1");
        Assert.assertEquals(cache.getOrLoad(1, key -> {
            suppliedLoaderCalls.incrementAndGet();
            return "supplied-" + key;
        }), "configured-1");
        Assert.assertEquals(suppliedLoaderCalls.get(), 0);
    }

    @Test(groups = "fast")
    public void testMaximumSizeBoundsEntries() {
        final Cache<Integer, String> cache = new CaffeineCache<>(2L, null, null);

        cache.put(1, "A");
        cache.put(2, "B");
        cache.put(3, "C");

        Assert.assertTrue(countPresent(cache, 1, 2, 3) <= 2);
    }

    @Test(groups = "fast")
    public void testMaximumSizeZeroDoesNotRetainEntries() {
        final AtomicInteger loadCount = new AtomicInteger();
        final Cache<Integer, String> cache = new CaffeineCache<>(0L, null, key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(loadCount.get(), 2);
    }

    @Test(groups = "fast")
    public void testExpireAfterWriteZeroDoesNotRetainEntries() {
        final AtomicInteger loadCount = new AtomicInteger();
        final Cache<Integer, String> cache = new CaffeineCache<>(null, Duration.ZERO, key -> {
            loadCount.incrementAndGet();
            return "loaded-" + key;
        });

        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(loadCount.get(), 2);
    }

    @Test(groups = "fast")
    public void testNullArgumentsRejected() {
        final Cache<Integer, String> cache = new CaffeineCache<>(null, null, null);

        assertThrowsNullPointerException(() -> cache.get(null), "Cannot #get() cache with key = null");
        assertThrowsNullPointerException(() -> cache.getOrLoad(1, null), "loader parameter in #getOrLoad() is null");
        assertThrowsNullPointerException(() -> cache.put(null, "value"), "key in #put() is null");
        assertThrowsNullPointerException(() -> cache.put(1, null), "value in #put() is null");
        assertThrowsNullPointerException(() -> cache.invalidate(null), "Cannot invalidate. Cache with null key is not allowed");
    }

    private int countPresent(final Cache<Integer, String> cache, final int... keys) {
        int present = 0;
        for (final int key : keys) {
            if (cache.get(key) != null) {
                present++;
            }
        }
        return present;
    }

    private void assertThrowsNullPointerException(final Runnable runnable, final String expectedMessage) {
        try {
            runnable.run();
            Assert.fail("Should reject null argument");
        } catch (final NullPointerException e) {
            Assert.assertEquals(e.getMessage(), expectedMessage);
        }
    }
}
