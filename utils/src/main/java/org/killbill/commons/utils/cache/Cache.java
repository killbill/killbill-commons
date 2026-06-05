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

public interface Cache <K, V> {

    /**
     * Get the cache value associated with {@code key}.
     *
     * Implementations may define additional behavior on a cache miss. For example, a loading implementation may load and
     * store a value before returning.
     *
     * @param key cache key
     * @return cache value, or null if no value can be found or loaded
     * @throws NullPointerException if key is null
     */
    V get(K key);

    /**
     * Get or load the cache value associated with {@code key}.
     *
     * The supplied {@code loader} gives the implementation a way to produce a value when no value is available through
     * its normal lookup or loading path. Implementations define:
     * <ul>
     *     <li>whether any configured loader or read-through source is consulted before {@code loader};</li>
     *     <li>whether a value returned by {@code loader} is stored in the cache;</li>
     *     <li>whether concurrent loads for the same key are deduplicated or atomic.</li>
     * </ul>
     *
     * @param key cache key
     * @param loader algorithm to load a value if this cache cannot find or load one
     * @return cache value, or value returned by {@code loader}
     * @throws NullPointerException if key or loader is null
     */
    V getOrLoad(K key, Function<K, V> loader);

    /**
     * Add value to cache.
     * @param key key
     * @param value value
     * @throws NullPointerException if key or value is null
     */
    void put(K key, V value);

    /**
     * Remove the cache based on its key.
     * @param key to remove.
     * @throws NullPointerException if key is null
     */
    void invalidate(K key);
}
