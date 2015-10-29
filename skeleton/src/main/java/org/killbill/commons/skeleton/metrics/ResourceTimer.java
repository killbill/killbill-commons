/*
 * Copyright 2010-2014 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package org.killbill.commons.skeleton.metrics;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class ResourceTimer {

    private static final Logger log = LoggerFactory.getLogger(ResourceTimer.class);

    private final int defaultStatusCode;
    private final LoadingCache<Integer, Timer> metrics;

    public ResourceTimer(final Future<MetricRegistry> metricsRegistryFuture,
                         final Class<?> klass,
                         final String name,
                         final int defaultStatusCode) {
        this.defaultStatusCode = defaultStatusCode;
        this.metrics = CacheBuilder.newBuilder()
                                   .build(new CacheLoader<Integer, Timer>() {
                                       @Override
                                       public Timer load(final Integer input) {
                                           try {
                                               return metricsRegistryFuture.get(1, TimeUnit.SECONDS).timer(MetricRegistry.name(klass, name + "-" + input));
                                           } catch (final InterruptedException ex) {
                                               Thread.currentThread().interrupt();
                                               return null;
                                           } catch (final TimeoutException ex) {
                                               throw new IllegalStateException("Received requests during guice initialization", ex);
                                           } catch (final ExecutionException ex) {
                                               throw new IllegalStateException(ex);
                                           }
                                       }
                                   });
    }

    public void update(final Integer statusCode, final long duration, final TimeUnit unit) {
        try {
            metrics.get(statusCode == null ? defaultStatusCode : statusCode).update(duration, unit);
        } catch (final ExecutionException e) {
            log.warn("Error while updating time", e);
        }
    }
}
