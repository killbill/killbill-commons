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
import java.util.function.Function;

import org.killbill.commons.utils.Preconditions;

/**
 * Builder for constructing {@link Cache} instances, supporting both programmatic configuration
 * and Guava-compatible spec string parsing via {@link #from(String)}.
 *
 * <p>Spec string format: comma-separated {@code key=value} pairs. Supported keys:</p>
 * <ul>
 *     <li>{@code maximumSize=N} — max cache entries, delegated to Caffeine (0 = do not retain entries)</li>
 *     <li>{@code concurrencyLevel=N} — accepted for compatibility, not used by Caffeine</li>
 *     <li>{@code expireAfterWrite=Nd|Nh|Nm|Ns} — time-to-live (d=days, h=hours, m=minutes, s=seconds, 0 = immediate expiry)</li>
 * </ul>
 *
 * <p>Example: {@code CacheBuilder.from("maximumSize=200,concurrencyLevel=8").build(loader)}</p>
 */
public final class CacheBuilder<K, V> {

    private Long maxSize;
    private Duration expireAfterWrite;

    private CacheBuilder() {
    }

    /**
     * Creates a new builder with default settings (unbounded, no TTL).
     */
    public static <K, V> CacheBuilder<K, V> newBuilder() {
        return new CacheBuilder<>();
    }

    /**
     * Creates a builder configured from a Guava-compatible spec string. Supported configurations are:
     * <code>maximumSize</code>, <code>concurrencyLevel</code> (implementation dependent) and
     * <code>expireAfterWrite</code>.
     *
     * <p>Ex: {@code CacheBuilder.from("maximumSize=200,concurrencyLevel=8,expireAfterWrite=20s").build(loader)}</p>
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
                    builder.maximumSize(Long.parseLong(value));
                    break;
                case "concurrencyLevel":
                    builder.concurrencyLevel(Integer.parseInt(value));
                    break;
                case "expireAfterWrite":
                    builder.expireAfterWrite(parseDuration(value));
                    break;
                default:
                    // Ignore unknown keys for forward compatibility
                    break;
            }
        }
        return builder;
    }

    /**
     * Sets the maximum number of entries. 0 means entries are not retained.
     */
    public CacheBuilder<K, V> maximumSize(final long maxSize) {
        Preconditions.checkArgument(maxSize >= 0, "maximumSize must be >= 0, got: %s", maxSize);
        this.maxSize = maxSize;
        return this;
    }

    /**
     * Accepts the concurrency level setting for Guava spec compatibility. Caffeine does not support this setting and
     * the value is ignored.
     */
    public CacheBuilder<K, V> concurrencyLevel(final int concurrencyLevel) {
        Preconditions.checkArgument(concurrencyLevel > 0, "concurrencyLevel must be > 0, got: %s", concurrencyLevel);
        return this;
    }

    /**
     * Sets the time-to-live for cache entries.
     *
     * @param duration TTL duration. 0 means immediate expiry.
     */
    public CacheBuilder<K, V> expireAfterWrite(final Duration duration) {
        Preconditions.checkNotNull(duration, "expireAfterWrite duration is null");
        Preconditions.checkArgument(!duration.isNegative(), "expireAfterWrite must be >= 0, got: %s", duration);
        this.expireAfterWrite = duration;
        return this;
    }

    /**
     * Sets the time-to-live for cache entries.
     *
     * @param duration TTL duration. 0 means immediate expiry.
     * @param unit TTL duration unit
     */
    public CacheBuilder<K, V> expireAfterWrite(final long duration, final TimeUnit unit) {
        Preconditions.checkArgument(duration >= 0, "expireAfterWrite must be >= 0, got: %s", duration);
        Preconditions.checkNotNull(unit, "expireAfterWrite unit is null");
        return expireAfterWrite(Duration.ofNanos(unit.toNanos(duration)));
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
        return new CaffeineCache<>(maxSize, expireAfterWrite, loader);
    }

    /**
     * Parses a duration string: a number followed by a unit (d=days, h=hours, m=minutes, s=seconds).
     * If no unit suffix, assumes seconds.
     */
    private static Duration parseDuration(final String value) {
        Preconditions.checkArgument(!value.isEmpty(), "expireAfterWrite value is empty");

        final char last = value.charAt(value.length() - 1);
        if (Character.isDigit(last)) {
            return Duration.ofSeconds(Long.parseLong(value));
        }

        final long number = Long.parseLong(value.substring(0, value.length() - 1));
        switch (last) {
            case 'd': return Duration.ofDays(number);
            case 'h': return Duration.ofHours(number);
            case 'm': return Duration.ofMinutes(number);
            case 's': return Duration.ofSeconds(number);
            default:
                throw new IllegalArgumentException("Unknown duration unit: " + last + " in '" + value + "'");
        }
    }
}
