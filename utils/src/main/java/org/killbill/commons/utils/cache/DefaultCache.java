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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.killbill.commons.utils.annotation.VisibleForTesting;

/**
 * <p>
 *     Default {@link Cache} implementation, that provide ability to:
 *     <ol>
 *         <li>Add maxSize to the cache. If more entry added, the oldest entry get removed automatically</li>
 *         <li>Add lazy-loading capability with {@code cacheLoader} parameter in constructor. This {@code cacheLoader}
 *             will be called if {@link #get(Object)} or {@link #getOrLoad(Object, Function)} return {@code null}</li>
 *         <li>Add timout (similar to expire-after-write in Guava and Caffeine) capability</li>
 *     </ol>
 * </p>
 * @param <K> cache key
 * @param <V> cache value
 */
public class DefaultCache<K, V> implements Cache<K, V> {

    public static final long NO_TIMEOUT = 0;

    @VisibleForTesting
    final Map<K, TimedValue<V>> map;

    /** Timeout value in second */
    private final long timeout;
    private final Function<K, V> cacheLoader;

    /**
     * Create cache with maximum entry size, without any timout (live forever) and no cache loader.
     *
     * @param maxSize max entry that should be existed in cache.
     */
    public DefaultCache(final int maxSize) {
        this(maxSize, NO_TIMEOUT, noCacheLoader());
    }

    /**
     * Create cache with maximum entry size, timeout (in second), and cacheLoader capability.
     *
     * @param maxSize cache maximum size. If more entry added, the oldest entry get removed automatically.
     * @param timeout
     * @param cacheLoader
     */
    public DefaultCache(final int maxSize, final long timeout, final Function<K, V> cacheLoader) {
        this.timeout = timeout * 1_000;
        this.cacheLoader = cacheLoader;

        map = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Entry<K, TimedValue<V>> eldest) {
                return size() > maxSize;
            }
        });
    }

    public static <K1, V1> Function<K1, V1> noCacheLoader() {
        return k1 -> null;
    }

    protected boolean isTimeoutEnabled() {
        return timeout > 0L;
    }

    protected boolean isCacheLoaderExist() {
        return !noCacheLoader().equals(cacheLoader);
    }

    protected void evictExpireEntry() {
        if (isTimeoutEnabled()) {
            map.values().removeIf(TimedValue::isTimeout);
        }
    }

    @Override
    public V get(final K key) {
        evictExpireEntry();

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

    @Override
    public V getOrLoad(final K key, final Function<K, V> loader) {
        final V value = get(key);
        return value == null ? loader.apply(key) : value;
    }

    @Override
    public void put(final K key, final V value) {
        evictExpireEntry();

        map.put(key, new TimedValue<>(timeout, value));
    }

    @Override
    public void invalidate(final K key) {
        map.remove(key);
    }
}
