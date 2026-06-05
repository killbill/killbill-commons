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
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.killbill.commons.utils.Preconditions;
import org.killbill.commons.utils.annotation.VisibleForTesting;

@VisibleForTesting
public final class CaffeineCache<K, V> implements Cache<K, V> {

    private final LoadingCache<K, V> delegate;

    CaffeineCache(final Long maxSize, final Duration expireAfterWrite, final Function<K, V> cacheLoader) {
        Preconditions.checkArgument(maxSize == null || maxSize >= 0, "cache maxSize should >= 0");
        Preconditions.checkArgument(expireAfterWrite == null || !expireAfterWrite.isNegative(), "cache expireAfterWrite should >= 0");

        final Caffeine<Object, Object> builder = Caffeine.newBuilder();
        builder.executor(Runnable::run);
        if (maxSize != null) {
            builder.maximumSize(maxSize);
        }
        if (expireAfterWrite != null) {
            builder.expireAfterWrite(expireAfterWrite);
        }

        this.delegate = builder.build(cacheLoader == null ? key -> null : cacheLoader::apply);
    }

    @Override
    public V get(final K key) {
        Preconditions.checkNotNull(key, "Cannot #get() cache with key = null");

        return delegate.get(key);
    }

    @Override
    public V getOrLoad(final K key, final Function<K, V> loader) {
        Preconditions.checkNotNull(key, "Cannot #getOrLoad() cache with key = null");
        Preconditions.checkNotNull(loader, "loader parameter in #getOrLoad() is null");

        return delegate.get(key, loader);
    }

    @Override
    public void put(final K key, final V value) {
        Preconditions.checkNotNull(key, "key in #put() is null");
        Preconditions.checkNotNull(value, "value in #put() is null");

        delegate.put(key, value);
    }

    @Override
    public void invalidate(final K key) {
        Preconditions.checkNotNull(key, "Cannot invalidate. Cache with null key is not allowed");

        delegate.invalidate(key);
    }
}
