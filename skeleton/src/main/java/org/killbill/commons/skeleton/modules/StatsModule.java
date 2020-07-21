/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.commons.skeleton.modules;

import org.killbill.commons.skeleton.metrics.TimedResourceListener;
import org.killbill.commons.skeleton.metrics.health.KillBillHealthCheckRegistry;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;
import com.google.inject.multibindings.Multibinder;
import com.palominolabs.metrics.guice.MetricsInstrumentationModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

public class StatsModule extends AbstractModule {

    private final String healthCheckUri;
    private final String metricsUri;
    private final String pingUri;
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
        this("/1.0/healthcheck", "/1.0/metrics", "/1.0/ping", "/1.0/threads", healthChecks);
    }

    public StatsModule(final String healthCheckUri, final String metricsUri, final String pingUri, final String threadsUri,
                       final Iterable<Class<? extends HealthCheck>> healthChecks) {
        this.healthCheckUri = healthCheckUri;
        this.metricsUri = metricsUri;
        this.pingUri = pingUri;
        this.threadsUri = threadsUri;
        this.healthChecks = healthChecks;
    }

    @Override
    protected void configure() {
        // Dropwizard metrics
        final MetricRegistry metricRegistry = createMetricRegistry();
        bind(MetricRegistry.class).toInstance(metricRegistry);
        install(MetricsInstrumentationModule.builder().withMetricRegistry(metricRegistry).build());

        // Dropwizard healthChecks
        final Multibinder<HealthCheck> healthChecksBinder = Multibinder.newSetBinder(binder(), HealthCheck.class);
        for (final Class<? extends HealthCheck> healthCheckClass : healthChecks) {
            healthChecksBinder.addBinding().to(healthCheckClass).asEagerSingleton();
        }
        install(new AdminServletModule(healthCheckUri, metricsUri, pingUri, threadsUri));

        // Metrics/Jersey integration
        final TimedResourceListener timedResourceTypeListener =
                new TimedResourceListener(getProvider(GuiceContainer.class), getProvider(MetricRegistry.class));
        bindListener(Matchers.any(), timedResourceTypeListener);

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

    /**
     * Override to provide a custom {@link MetricRegistry}
     *
     * @return MetricRegistry instance to bind
     */
    protected MetricRegistry createMetricRegistry() {
        return new MetricRegistry();
    }
}
