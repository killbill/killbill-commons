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

package org.killbill.commons.metrics.modules;

import org.killbill.commons.health.api.HealthCheck;
import org.killbill.commons.health.api.HealthCheckRegistry;
import org.killbill.commons.metrics.health.KillBillHealthCheckRegistry;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class StatsModule extends AbstractModule {

    private final String healthCheckUri;
    private final String metricsUri;
    private final String threadsUri;
    private final Iterable<Class<? extends HealthCheck>> healthChecks;

    public StatsModule() {
        this(ImmutableList.<Class<? extends HealthCheck>>of());
    }

    public StatsModule(final Class<? extends HealthCheck> healthCheck) {
        this(ImmutableList.<Class<? extends HealthCheck>>of(healthCheck));
    }

    public StatsModule(final Class<? extends HealthCheck>... healthChecks) {
        this(ImmutableList.<Class<? extends HealthCheck>>copyOf(healthChecks));
    }

    public StatsModule(final Iterable<Class<? extends HealthCheck>> healthChecks) {
        this("/1.0/healthcheck", "/1.0/metrics", "/1.0/threads", healthChecks);
    }

    public StatsModule(final String healthCheckUri,
                       final String metricsUri,
                       final String threadsUri,
                       final Iterable<Class<? extends HealthCheck>> healthChecks) {
        this.healthCheckUri = healthCheckUri;
        this.metricsUri = metricsUri;
        this.threadsUri = threadsUri;
        this.healthChecks = healthChecks;
    }

    @Override
    protected void configure() {
        final Multibinder<HealthCheck> healthChecksBinder = Multibinder.newSetBinder(binder(), HealthCheck.class);
        for (final Class<? extends HealthCheck> healthCheckClass : healthChecks) {
            healthChecksBinder.addBinding().to(healthCheckClass).asEagerSingleton();
        }
        install(new AdminServletModule(healthCheckUri, metricsUri, threadsUri));

        bind(HealthCheckRegistry.class).toInstance(createHealthCheckRegistry());
    }

    /**
     * Override to provide a custom {@link HealthCheckRegistry}
     *
     * @return HealthCheckRegistry instance to bind
     */
    protected HealthCheckRegistry createHealthCheckRegistry() {
        return new KillBillHealthCheckRegistry();
    }
}
