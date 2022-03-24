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

package org.killbill.commons.jdbi.hikari;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.killbill.commons.health.api.HealthCheck;
import org.killbill.commons.health.api.HealthCheckRegistry;
import org.killbill.commons.health.api.Result;
import org.killbill.commons.health.impl.HealthyResultBuilder;
import org.killbill.commons.health.impl.UnhealthyResultBuilder;
import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.api.Timer;

import com.zaxxer.hikari.HikariConfig;

/**
 * Provides HealthChecks.  Two health checks are provided:
 * <ul>
 *   <li>ConnectivityCheck</li>
 *   <li>Connection99Percent</li>
 * </ul>
 * The Connection99Percent health check will only be registered if the health check property
 * <code>expected99thPercentileMs</code> is defined and greater than 0.
 */
public final class KillBillHealthChecker {

    private KillBillHealthChecker() {
        // private constructor
    }

    /**
     * Register health checks.
     *
     * @param dataSource   the DataSource to register health checks for
     * @param hikariConfig the pool configuration
     * @param registry     the HealthCheckRegistry into which checks will be registered
     */
    public static void registerHealthChecks(final DataSource dataSource, final HikariConfig hikariConfig, final HealthCheckRegistry registry) {
        final Properties healthCheckProperties = hikariConfig.getHealthCheckProperties();
        final MetricRegistry metricRegistry = (MetricRegistry) hikariConfig.getMetricRegistry();

        registry.register(String.format("%s.%s.%s", hikariConfig.getPoolName(), "pool", "ConnectivityCheck"), new ConnectivityHealthCheck(dataSource));

        final long expected99thPercentile = Long.parseLong(healthCheckProperties.getProperty("expected99thPercentileMs", "0"));
        if (metricRegistry != null && expected99thPercentile > 0) {
            for (final Entry<String, Timer> entry : metricRegistry.getTimers().entrySet()) {
                if (entry.getKey().equals(String.format("%s.%s.%s", hikariConfig.getPoolName(), "pool", "Wait"))) {
                    registry.register(String.format("%s.%s.%s", hikariConfig.getPoolName(), "pool", "Connection99Percent"), new Connection99Percent(entry.getValue(), expected99thPercentile));
                }
            }
        }
    }

    private static class ConnectivityHealthCheck implements HealthCheck {

        private final DataSource dataSource;

        ConnectivityHealthCheck(final DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public Result check() throws Exception {
            try (final Connection ignored = dataSource.getConnection()) {
                return new HealthyResultBuilder().createHealthyResult();
            } catch (final SQLException e) {
                return new UnhealthyResultBuilder().setError(e).createUnhealthyResult();
            }
        }
    }

    private static class Connection99Percent implements HealthCheck {

        private final Timer waitTimer;
        private final long expected99thPercentile;

        Connection99Percent(final Timer waitTimer, final long expected99thPercentile) {
            this.waitTimer = waitTimer;
            this.expected99thPercentile = expected99thPercentile;
        }

        @Override
        public Result check() throws Exception {
            final long the99thPercentile = TimeUnit.NANOSECONDS.toMillis(Math.round(waitTimer.getSnapshot().get99thPercentile()));
            return the99thPercentile <= expected99thPercentile ? new HealthyResultBuilder().createHealthyResult() : new UnhealthyResultBuilder().setMessage(String.format("99th percentile connection wait time of %dms exceeds the threshold %dms", the99thPercentile, expected99thPercentile)).createUnhealthyResult();
        }
    }
}
