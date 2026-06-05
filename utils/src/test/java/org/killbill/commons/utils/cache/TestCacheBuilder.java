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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestCacheBuilder {

    // --- newBuilder() ---

    @Test(groups = "fast")
    public void testNewBuilderCreatesUnboundedCache() {
        final Cache<String, String> cache = CacheBuilder.<String, String>newBuilder().build();
        cache.put("a", "1");
        Assert.assertEquals(cache.get("a"), "1");
    }

    @Test(groups = "fast")
    public void testNewBuilderWithLoader() {
        final Cache<Integer, String> cache = CacheBuilder.<Integer, String>newBuilder()
                .build(key -> "loaded-" + key);

        Assert.assertEquals(cache.get(1), "loaded-1");
        // Second call should serve from cache
        Assert.assertEquals(cache.get(1), "loaded-1");
    }

    @Test(groups = "fast")
    public void testNewBuilderWithMaximumSize() {
        final Cache<Integer, String> cache = CacheBuilder.<Integer, String>newBuilder()
                .maximumSize(2)
                .build();

        cache.put(1, "A");
        cache.put(2, "B");
        cache.put(3, "C");

        Assert.assertTrue(countPresent(cache, 1, 2, 3) <= 2);
    }

    @Test(groups = "fast")
    public void testNewBuilderMaximumSizeZero() {
        final AtomicInteger loadCount = new AtomicInteger(0);
        final Cache<Integer, String> cache = CacheBuilder.<Integer, String>newBuilder()
                .maximumSize(0)
                .build(key -> {
                    loadCount.incrementAndGet();
                    return "loaded-" + key;
                });

        // Always calls loader, never caches
        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(loadCount.get(), 2);
    }

    @Test(groups = "fast")
    public void testNewBuilderWithExpireAfterWrite() throws InterruptedException {
        final Cache<Integer, String> cache = CacheBuilder.<Integer, String>newBuilder()
                .expireAfterWrite(Duration.ofSeconds(1))
                .build();

        cache.put(1, "A");
        Assert.assertEquals(cache.get(1), "A");

        Thread.sleep(1100);
        Assert.assertNull(cache.get(1));
    }

    @Test(groups = "fast")
    public void testNewBuilderWithExpireAfterWriteDurationZero() {
        final AtomicInteger loadCount = new AtomicInteger(0);
        final Cache<Integer, String> cache = CacheBuilder.<Integer, String>newBuilder()
                .expireAfterWrite(Duration.ZERO)
                .build(key -> {
                    loadCount.incrementAndGet();
                    return "loaded-" + key;
                });

        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(loadCount.get(), 2);
    }

    @Test(groups = "fast")
    public void testNewBuilderWithExpireAfterWriteTimeUnit() throws InterruptedException {
        final Cache<Integer, String> cache = CacheBuilder.<Integer, String>newBuilder()
                .expireAfterWrite(1, TimeUnit.SECONDS)
                .build();

        cache.put(1, "A");
        Assert.assertEquals(cache.get(1), "A");

        Thread.sleep(1100);
        Assert.assertNull(cache.get(1));
    }

    @Test(groups = "fast")
    public void testNewBuilderWithConcurrencyLevel() {
        final Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                .concurrencyLevel(8)
                .build();

        cache.put("x", "y");
        Assert.assertEquals(cache.get("x"), "y");
    }

    // --- from(spec) ---

    @Test(groups = "fast")
    public void testFromSpecMaximumSize() {
        final Cache<Integer, String> cache = CacheBuilder.<Integer, String>from("maximumSize=2")
                .build();

        cache.put(1, "A");
        cache.put(2, "B");
        cache.put(3, "C");

        Assert.assertTrue(countPresent(cache, 1, 2, 3) <= 2);
    }

    @Test(groups = "fast")
    public void testFromSpecConcurrencyLevel() {
        final Cache<String, String> cache = CacheBuilder.<String, String>from("concurrencyLevel=4")
                .build();

        cache.put("a", "1");
        Assert.assertEquals(cache.get("a"), "1");
    }

    @Test(groups = "fast")
    public void testFromSpecExpireAfterWriteSeconds() throws InterruptedException {
        final Cache<Integer, String> cache = CacheBuilder.<Integer, String>from("expireAfterWrite=1s")
                .build();

        cache.put(1, "A");
        Assert.assertEquals(cache.get(1), "A");

        Thread.sleep(1100);
        Assert.assertNull(cache.get(1));
    }

    @Test(groups = "fast")
    public void testFromSpecExpireAfterWriteMinutes() {
        // Just verify parsing doesn't throw — won't actually wait 60s
        final Cache<String, String> cache = CacheBuilder.<String, String>from("expireAfterWrite=2m")
                .build();

        cache.put("x", "y");
        Assert.assertEquals(cache.get("x"), "y");
    }

    @Test(groups = "fast")
    public void testFromSpecExpireAfterWriteHours() {
        final Cache<String, String> cache = CacheBuilder.<String, String>from("expireAfterWrite=1h")
                .build();
        cache.put("x", "y");
        Assert.assertEquals(cache.get("x"), "y");
    }

    @Test(groups = "fast")
    public void testFromSpecExpireAfterWriteDays() {
        final Cache<String, String> cache = CacheBuilder.<String, String>from("expireAfterWrite=1d")
                .build();
        cache.put("x", "y");
        Assert.assertEquals(cache.get("x"), "y");
    }

    @Test(groups = "fast")
    public void testFromSpecExpireAfterWriteNoUnit() throws InterruptedException {
        // No unit suffix = seconds
        final Cache<Integer, String> cache = CacheBuilder.<Integer, String>from("expireAfterWrite=1")
                .build();

        cache.put(1, "A");
        Thread.sleep(1100);
        Assert.assertNull(cache.get(1));
    }

    @Test(groups = "fast")
    public void testFromSpecExpireAfterWriteZero() {
        final AtomicInteger loadCount = new AtomicInteger(0);
        final Cache<Integer, String> cache = CacheBuilder.<Integer, String>from("expireAfterWrite=0")
                .build(key -> {
                    loadCount.incrementAndGet();
                    return "loaded-" + key;
                });

        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(cache.get(1), "loaded-1");
        Assert.assertEquals(loadCount.get(), 2);
    }

    @Test(groups = "fast")
    public void testFromSpecMultipleKeys() {
        final Cache<Integer, String> cache = CacheBuilder.<Integer, String>from(
                "concurrencyLevel=8,maximumSize=3").build();

        cache.put(1, "A");
        cache.put(2, "B");
        cache.put(3, "C");
        cache.put(4, "D");

        Assert.assertTrue(countPresent(cache, 1, 2, 3, 4) <= 3);
    }

    @Test(groups = "fast")
    public void testFromSpecWithLoader() {
        final Cache<Integer, String> cache = CacheBuilder.<Integer, String>from("maximumSize=100")
                .build(key -> "loaded-" + key);

        Assert.assertEquals(cache.get(42), "loaded-42");
    }

    @Test(groups = "fast")
    public void testFromSpecEmptyString() {
        // Empty spec = default (unbounded, no TTL)
        final Cache<String, String> cache = CacheBuilder.<String, String>from("").build();
        cache.put("a", "1");
        Assert.assertEquals(cache.get("a"), "1");
    }

    @Test(groups = "fast")
    public void testFromSpecIgnoresUnknownKeys() {
        // Unknown keys should be silently ignored
        final Cache<String, String> cache = CacheBuilder.<String, String>from(
                "maximumSize=10,recordStats,weakKeys").build();
        cache.put("a", "1");
        Assert.assertEquals(cache.get("a"), "1");
    }

    // --- Validation ---

    @Test(groups = "fast")
    public void testMaximumSizeRejectsNegative() {
        try {
            CacheBuilder.<String, String>newBuilder().maximumSize(-1);
            Assert.fail("Should reject negative maximumSize");
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("maximumSize must be >= 0"));
        }
    }

    @Test(groups = "fast")
    public void testConcurrencyLevelRejectsZero() {
        try {
            CacheBuilder.<String, String>newBuilder().concurrencyLevel(0);
            Assert.fail("Should reject zero concurrencyLevel");
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("concurrencyLevel must be > 0"));
        }
    }

    @Test(groups = "fast")
    public void testExpireAfterWriteRejectsNegative() {
        try {
            CacheBuilder.<String, String>newBuilder().expireAfterWrite(-1, TimeUnit.SECONDS);
            Assert.fail("Should reject negative expireAfterWrite");
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("expireAfterWrite must be >= 0"));
        }
    }

    @Test(groups = "fast")
    public void testExpireAfterWriteDurationRejectsNegative() {
        try {
            CacheBuilder.<String, String>newBuilder().expireAfterWrite(Duration.ofNanos(-1));
            Assert.fail("Should reject negative expireAfterWrite");
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("expireAfterWrite must be >= 0"));
        }
    }

    @Test(groups = "fast")
    public void testExpireAfterWriteDurationRejectsNull() {
        try {
            CacheBuilder.<String, String>newBuilder().expireAfterWrite((Duration) null);
            Assert.fail("Should reject null expireAfterWrite duration");
        } catch (final NullPointerException e) {
            Assert.assertEquals(e.getMessage(), "expireAfterWrite duration is null");
        }
    }

    @Test(groups = "fast")
    public void testExpireAfterWriteTimeUnitRejectsNullUnit() {
        try {
            CacheBuilder.<String, String>newBuilder().expireAfterWrite(1, null);
            Assert.fail("Should reject null expireAfterWrite unit");
        } catch (final NullPointerException e) {
            Assert.assertEquals(e.getMessage(), "expireAfterWrite unit is null");
        }
    }

    @Test(groups = "fast")
    public void testFromSpecNullThrows() {
        try {
            CacheBuilder.from(null);
            Assert.fail("Should reject null spec");
        } catch (final NullPointerException e) {
            Assert.assertEquals(e.getMessage(), "spec is null");
        }
    }

    @Test(groups = "fast")
    public void testFromSpecInvalidDurationUnit() {
        try {
            CacheBuilder.<String, String>from("expireAfterWrite=10x").build();
            Assert.fail("Should reject invalid duration unit");
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Unknown duration unit"));
        }
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
}
