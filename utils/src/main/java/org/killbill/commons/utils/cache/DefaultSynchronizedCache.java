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

import java.util.function.Function;

/**
 * {@link DefaultCache} that synchronize {@link Cache} methods call.
 */
public class DefaultSynchronizedCache<K, V> extends DefaultCache<K, V> implements Cache<K, V> {

    public DefaultSynchronizedCache(final int maxSize) {
        super(maxSize);
    }

    public DefaultSynchronizedCache(final Function<K, V> cacheLoader) {
        super(cacheLoader);
    }

    public DefaultSynchronizedCache(final int maxSize, final long timeoutInSecond, final Function<K, V> cacheLoader) {
        super(maxSize, timeoutInSecond, cacheLoader);
    }

    @Override
    public V get(final K key) {
        synchronized (this) {
            return super.get(key);
        }
    }

    @Override
    public V getOrLoad(final K key, final Function<K, V> loader) {
        synchronized (this) {
            return super.getOrLoad(key, loader);
        }
    }

    @Override
    public void put(final K key, final V value) {
        synchronized (this) {
            super.put(key, value);
        }
    }

    @Override
    public void invalidate(final K key) {
        synchronized (this) {
            super.invalidate(key);
        }
    }
}
