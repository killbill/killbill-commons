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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.killbill.commons.utils.Preconditions;
import org.killbill.commons.utils.annotation.VisibleForTesting;

/**
 * A {@link Cache} implementation backed by {@link ConcurrentHashMap}, providing lock-free reads
 * and fine-grained locking on writes. Supports:
 * <ul>
 *     <li>Bounded size with LRU eviction (based on last-access timestamp)</li>
 *     <li>Time-to-live expiration (lazy eviction on access)</li>
 *     <li>Autoloading via a {@code cacheLoader} function on {@link #get(Object)}</li>
 *     <li>Ad-hoc loading via parameter on {@link #getOrLoad(Object, Function)} (ignores constructor loader)</li>
 *     <li>Configurable {@code concurrencyLevel} hint passed to ConcurrentHashMap</li>
 * </ul>
 *
 * <p>A {@code maxSize} of 0 means no caching: the loader is always called and results are never stored.</p>
 *
 * @param <K> cache key type
 * @param <V> cache value type
 */
public class ConcurrentCache<K, V> implements Cache<K, V> {

    public static final long NO_TIMEOUT = 0;

    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final int DEFAULT_CONCURRENCY_LEVEL = 16;

    @VisibleForTesting
    final ConcurrentHashMap<K, CacheEntry<V>> map;

    private final int maxSize;
    private final long timeToLiveMillis;
    private final Function<K, V> cacheLoader;

    /**
     * Create an unbounded cache without timeout or loader.
     */
    public ConcurrentCache() {
        this(Integer.MAX_VALUE, NO_TIMEOUT, DEFAULT_CONCURRENCY_LEVEL, null);
    }

    /**
     * Create a bounded cache without timeout or loader.
     *
     * @param maxSize maximum entries. 0 means no caching (loader always called, nothing stored).
     */
    public ConcurrentCache(final int maxSize) {
        this(maxSize, NO_TIMEOUT, DEFAULT_CONCURRENCY_LEVEL, null);
    }

    /**
     * Create an unbounded cache with a loader.
     *
     * @param cacheLoader function to load values on cache miss via {@link #get(Object)}.
     *                    May be null (no autoloading on get).
     */
    public ConcurrentCache(final Function<K, V> cacheLoader) {
        this(Integer.MAX_VALUE, NO_TIMEOUT, DEFAULT_CONCURRENCY_LEVEL, cacheLoader);
    }

    /**
     * Create a bounded cache with timeout and loader.
     *
     * @param maxSize maximum entries. 0 means no caching.
     * @param timeoutInSecond TTL in seconds. 0 means no expiry.
     * @param cacheLoader function to load values on cache miss via {@link #get(Object)}. May be null.
     */
    public ConcurrentCache(final int maxSize, final long timeoutInSecond, final Function<K, V> cacheLoader) {
        this(maxSize, timeoutInSecond, DEFAULT_CONCURRENCY_LEVEL, cacheLoader);
    }

    /**
     * Full constructor with all parameters.
     *
     * @param maxSize maximum entries. 0 means no caching.
     * @param timeoutInSecond TTL in seconds. 0 means no expiry.
     * @param concurrencyLevel hint for ConcurrentHashMap internal sizing.
     * @param cacheLoader function to load values on cache miss via {@link #get(Object)}. May be null.
     */
    public ConcurrentCache(final int maxSize, final long timeoutInSecond, final int concurrencyLevel,
                           final Function<K, V> cacheLoader) {
        Preconditions.checkArgument(maxSize >= 0, "cache maxSize should >= 0");
        Preconditions.checkArgument(timeoutInSecond >= 0, "cache timeoutInSecond should >= 0");
        Preconditions.checkArgument(concurrencyLevel > 0, "cache concurrencyLevel should > 0");

        this.maxSize = maxSize;
        this.timeToLiveMillis = timeoutInSecond * 1_000;
        this.cacheLoader = cacheLoader;
        this.map = new ConcurrentHashMap<>(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, concurrencyLevel);
    }

    /**
     * Returns the cached value for {@code key}, or loads it via the constructor-defined {@code cacheLoader}.
     * If loaded, the value is stored in the cache.
     *
     * @return cached or loaded value, or null if no loader or loader returns null
     */
    @Override
    public V get(final K key) {
        Preconditions.checkNotNull(key, "Cannot #get() cache with key = null");

        final V cached = getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        if (cacheLoader != null) {
            final V value = cacheLoader.apply(key);
            if (value != null) {
                put(key, value);
            }
            return value;
        }
        return null;
    }

    /**
     * Returns the cached value for {@code key}. On a cache miss, calls the supplied {@code loader}
     * (ignoring the constructor-defined cacheLoader) and stores the result in the cache.
     *
     * @return cached or loaded value, or null if loader returns null
     */
    @Override
    public V getOrLoad(final K key, final Function<K, V> loader) {
        Preconditions.checkNotNull(key, "Cannot #getOrLoad() cache with key = null");
        Preconditions.checkNotNull(loader, "loader parameter in #getOrLoad() is null");

        final V cached = getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        final V value = loader.apply(key);
        if (value != null) {
            put(key, value);
        }
        return value;
    }

    /**
     * Returns the cached value for {@code key} if present and not expired, or null.
     * Evicts expired entries on access and updates the last-accessed timestamp on hit.
     */
    @VisibleForTesting
    V getIfPresent(final K key) {
        final CacheEntry<V> entry = map.get(key);
        if (entry != null) {
            if (isExpired(entry)) {
                map.remove(key);
            } else {
                entry.lastAccessedAt = System.currentTimeMillis();
                return entry.value;
            }
        }
        return null;
    }

    @Override
    public void put(final K key, final V value) {
        Preconditions.checkNotNull(key, "key in #put() is null");
        Preconditions.checkNotNull(value, "value in #put() is null");

        if (maxSize == 0) {
            return;
        }

        map.put(key, new CacheEntry<>(value, computeExpiresAt()));
        evictIfOverSize();
    }

    @Override
    public void invalidate(final K key) {
        Preconditions.checkNotNull(key, "Cannot invalidate. Cache with null key is not allowed");
        map.remove(key);
    }

    private boolean isExpired(final CacheEntry<V> entry) {
        return timeToLiveMillis > 0 && System.currentTimeMillis() >= entry.expiresAt;
    }

    private long computeExpiresAt() {
        if (timeToLiveMillis <= 0) {
            return Long.MAX_VALUE;
        }
        return System.currentTimeMillis() + timeToLiveMillis;
    }

    private void evictIfOverSize() {
        while (map.size() > maxSize) {
            K oldestKey = null;
            long oldestAccess = Long.MAX_VALUE;
            for (final Map.Entry<K, CacheEntry<V>> e : map.entrySet()) {
                if (e.getValue().lastAccessedAt < oldestAccess) {
                    oldestAccess = e.getValue().lastAccessedAt;
                    oldestKey = e.getKey();
                }
            }
            if (oldestKey != null) {
                map.remove(oldestKey);
            } else {
                break;
            }
        }
    }

    /**
     * Internal cache entry holding the value, expiration deadline, and last access timestamp.
     */
    static class CacheEntry<V> {
        final V value;
        final long expiresAt;
        long lastAccessedAt;

        CacheEntry(final V value, final long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
            this.lastAccessedAt = System.currentTimeMillis();
        }
    }
}
