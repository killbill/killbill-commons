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

import java.util.function.Function;

import org.killbill.commons.utils.Preconditions;

/**
 * Builder for constructing {@link ConcurrentCache} instances, supporting both programmatic configuration
 * and Guava-compatible spec string parsing via {@link #from(String)}.
 *
 * <p>Spec string format: comma-separated {@code key=value} pairs. Supported keys:</p>
 * <ul>
 *     <li>{@code maximumSize=N} — max cache entries (0 = no caching)</li>
 *     <li>{@code concurrencyLevel=N} — ConcurrentHashMap sizing hint</li>
 *     <li>{@code expireAfterWrite=Nd|Nh|Nm|Ns} — time-to-live (d=days, h=hours, m=minutes, s=seconds)</li>
 * </ul>
 *
 * <p>Example: {@code CacheBuilder.from("maximumSize=200,concurrencyLevel=8").build(loader)}</p>
 */
public final class CacheBuilder<K, V> {

    private static final int DEFAULT_CONCURRENCY_LEVEL = 16;

    private int maxSize = Integer.MAX_VALUE;
    private long timeoutInSecond = ConcurrentCache.NO_TIMEOUT;
    private int concurrencyLevel = DEFAULT_CONCURRENCY_LEVEL;

    private CacheBuilder() {
    }

    /**
     * Creates a new builder with default settings (unbounded, no TTL).
     */
    public static <K, V> CacheBuilder<K, V> newBuilder() {
        return new CacheBuilder<>();
    }

    /**
     * Creates a builder configured from a Guava-compatible spec string.
     *
     * @param spec comma-separated key=value pairs (e.g. "maximumSize=200,concurrencyLevel=8")
     * @return configured builder
     * @throws IllegalArgumentException if the spec contains invalid values
     */
    public static <K, V> CacheBuilder<K, V> from(final String spec) {
        Preconditions.checkNotNull(spec, "spec is null");
        final CacheBuilder<K, V> builder = new CacheBuilder<>();

        if (spec.trim().isEmpty()) {
            return builder;
        }

        for (final String token : spec.split(",")) {
            final String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final int eq = trimmed.indexOf('=');
            if (eq < 0) {
                // Bare key (e.g. "recordStats") — ignore
                continue;
            }

            final String key = trimmed.substring(0, eq).trim();
            final String value = trimmed.substring(eq + 1).trim();

            switch (key) {
                case "maximumSize":
                    builder.maximumSize(Integer.parseInt(value));
                    break;
                case "concurrencyLevel":
                    builder.concurrencyLevel(Integer.parseInt(value));
                    break;
                case "expireAfterWrite":
                    builder.timeoutInSecond = parseDuration(value);
                    break;
                default:
                    // Ignore unknown keys for forward compatibility
                    break;
            }
        }
        return builder;
    }

    /**
     * Sets the maximum number of entries. 0 means no caching (loader called every time, nothing stored).
     */
    public CacheBuilder<K, V> maximumSize(final long maxSize) {
        Preconditions.checkArgument(maxSize >= 0, "maximumSize must be >= 0, got: %s", maxSize);
        this.maxSize = (int) Math.min(maxSize, Integer.MAX_VALUE);
        return this;
    }

    /**
     * Sets the concurrency level hint for the underlying ConcurrentHashMap.
     */
    public CacheBuilder<K, V> concurrencyLevel(final int concurrencyLevel) {
        Preconditions.checkArgument(concurrencyLevel > 0, "concurrencyLevel must be > 0, got: %s", concurrencyLevel);
        this.concurrencyLevel = concurrencyLevel;
        return this;
    }

    /**
     * Sets the time-to-live for cache entries.
     *
     * @param timeoutInSecond TTL in seconds. 0 means no expiry.
     */
    public CacheBuilder<K, V> expireAfterWrite(final long timeoutInSecond) {
        Preconditions.checkArgument(timeoutInSecond >= 0, "expireAfterWrite must be >= 0, got: %s", timeoutInSecond);
        this.timeoutInSecond = timeoutInSecond;
        return this;
    }

    /**
     * Builds a cache without a loader. Values must be populated via {@link Cache#put} or
     * {@link Cache#getOrLoad}.
     */
    public Cache<K, V> build() {
        return build(null);
    }

    /**
     * Builds a cache with a loader that is called on {@link Cache#get} misses.
     *
     * @param loader function to load values on cache miss. May be null.
     */
    public Cache<K, V> build(final Function<K, V> loader) {
        return new ConcurrentCache<>(maxSize, timeoutInSecond, concurrencyLevel, loader);
    }

    /**
     * Parses a duration string: a number followed by a unit (d=days, h=hours, m=minutes, s=seconds).
     * If no unit suffix, assumes seconds.
     */
    private static long parseDuration(final String value) {
        Preconditions.checkArgument(!value.isEmpty(), "expireAfterWrite value is empty");

        final char last = value.charAt(value.length() - 1);
        if (Character.isDigit(last)) {
            return Long.parseLong(value);
        }

        final long number = Long.parseLong(value.substring(0, value.length() - 1));
        switch (last) {
            case 'd': return number * 86400;
            case 'h': return number * 3600;
            case 'm': return number * 60;
            case 's': return number;
            default:
                throw new IllegalArgumentException("Unknown duration unit: " + last + " in '" + value + "'");
        }
    }
}
