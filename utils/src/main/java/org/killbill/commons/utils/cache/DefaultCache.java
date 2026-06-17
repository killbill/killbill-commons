/*
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

package org.killbill.commons.utils.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.killbill.commons.utils.Preconditions;
import org.killbill.commons.utils.annotation.VisibleForTesting;

/**
 * <strong>deprecated</scrong>. Use {@link CaffeineCache} instead via {@link CacheBuilder}.
 * <p>
 *     Default {@link Cache} implementation, that provides:
 *     <ol>
 *         <li>A maximum size. If more entries are added, the least recently used entry is removed automatically.</li>
 *         <li>
 *             Lazy-loading capability with the {@code cacheLoader} constructor parameter. This {@code cacheLoader} will
 *             be called if no value is found in the cache. Non-null values returned by {@code cacheLoader} are stored in
 *             the cache. {@code cacheLoader} also takes precedence over the loader defined in
 *             {@link #getOrLoad(Object, Function)}.
 *         </li>
 *         <li>
 *             Expire-after-write capability. Accessing an entry does not extend its lifetime. Expired entries are
 *             evicted lazily when accessed.
 *         </li>
 *     </ol>
 * </p>
 * @param <K> cache key
 * @param <V> cache value
 */
@Deprecated
public class DefaultCache<K, V> implements Cache<K, V> {

    public static final long NO_TIMEOUT = 0;

    @VisibleForTesting
    final Map<K, TimedValue<V>> map;

    private final long timeoutMillis;
    private final Function<K, V> cacheLoader;

    /**
     * Create cache with maximum entry size, no expiration, and no cache loader.
     *
     * @param maxSize maximum number of entries to keep in cache
     */
    public DefaultCache(final int maxSize) {
        this(maxSize, NO_TIMEOUT, noCacheLoader());
    }

    /**
     * Create cache with {@code maxSize = Integer.MAX_VALUE}, {@code timeoutInSecond = NO_TIMEOUT}, and with supplied
     * {@code cacheLoader}.
     *
     * @param cacheLoader cache loader. Use {@link #noCacheLoader()} to make this cache should not attempt to load
     *                    anything if value is null.
     */
    public DefaultCache(final Function<K, V> cacheLoader) {
        this(Integer.MAX_VALUE, NO_TIMEOUT, cacheLoader);
    }

    /**
     * Create cache with maximum entry size, expire-after-write duration, and cacheLoader capability.
     *
     * @param maxSize cache maximum size. If more entries are added, the least recently used entry is removed
     *                automatically.
     * @param timeoutInSecond expire-after-write duration in seconds. Entries expire after this duration from the time
     *                        they are written; reads do not extend the expiration time. Expired entries are evicted
     *                        lazily when accessed. Use {@link DefaultCache#NO_TIMEOUT} to make entries live forever.
     * @param cacheLoader cache loader. Use {@link #noCacheLoader()} to make this cache should not attempt to load
     *                    anything if value is null.
     */
    public DefaultCache(final int maxSize, final long timeoutInSecond, final Function<K, V> cacheLoader) {
        Preconditions.checkArgument(maxSize > 0, "cache maxSize should > 0");
        Preconditions.checkArgument(timeoutInSecond >= 0, "cache timeoutInSecond should >= 0");

        this.timeoutMillis = timeoutInSecond * 1_000;
        this.cacheLoader = Preconditions.checkNotNull(cacheLoader, "cacheLoader is null. Use DefaultCache#noCacheLoader() to create a cache without loader");

        map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Entry<K, TimedValue<V>> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * Create {@link DefaultCache} without any loader.
     */
    public static <K1, V1> Function<K1, V1> noCacheLoader() {
        return k1 -> null;
    }

    protected boolean isTimeoutEnabled() {
        return timeoutMillis > 0L;
    }

    protected boolean isCacheLoaderExist() {
        return !noCacheLoader().equals(cacheLoader);
    }

    protected void evictExpireEntry(final K key) {
        if (isTimeoutEnabled()) {
            final TimedValue<V> value = map.get(key);
            if (value != null && value.isTimeout()) {
                invalidate(key);
            }
        }
    }

    @Override
    public V get(final K key) {
        Preconditions.checkNotNull(key, "Cannot #get() cache with key = null");

        evictExpireEntry(key);

        final TimedValue<V> timedValue = map.get(key);
        if (timedValue != null) {
            return timedValue.getValue();
        } else if (isCacheLoaderExist()) {
            final V value = cacheLoader.apply(key);
            if (value != null) {
                put(key, value);
            }
            return value;
        } else {
            return null;
        }
    }

    /**
     * Returns the value from {@link #get(Object)} if one can be found or loaded. If {@link #get(Object)} returns
     * {@code null}, this method calls {@code loader} and returns its result without storing that result in this cache.
     */
    @Override
    public V getOrLoad(final K key, final Function<K, V> loader) {
        Preconditions.checkNotNull(loader, "loader parameter in #getOrLoad() is null");
        final V value = get(key);
        return value == null ? loader.apply(key) : value;
    }

    @Override
    public void put(final K key, final V value) {
        Preconditions.checkNotNull(key, "key in #put() is null");
        Preconditions.checkNotNull(value, "value in #put() is null");

        map.put(key, new TimedValue<>(timeoutMillis, value));
    }

    @Override
    public void invalidate(final K key) {
        Preconditions.checkNotNull(key, "Cannot invalidate. Cache with null key is not allowed");
        map.remove(key);
    }
}
