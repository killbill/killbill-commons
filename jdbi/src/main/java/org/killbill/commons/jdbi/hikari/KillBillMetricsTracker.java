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

import java.util.concurrent.TimeUnit;

import org.killbill.commons.metrics.api.Gauge;
import org.killbill.commons.metrics.api.Histogram;
import org.killbill.commons.metrics.api.Meter;
import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.api.Timer;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.PoolStats;

public class KillBillMetricsTracker implements IMetricsTracker {

    private static final String METRIC_CATEGORY = "pool";
    private static final String METRIC_NAME_WAIT = "Wait";
    private static final String METRIC_NAME_USAGE = "Usage";
    private static final String METRIC_NAME_CONNECT = "ConnectionCreation";
    private static final String METRIC_NAME_TIMEOUT_RATE = "ConnectionTimeoutRate";
    private static final String METRIC_NAME_TOTAL_CONNECTIONS = "TotalConnections";
    private static final String METRIC_NAME_IDLE_CONNECTIONS = "IdleConnections";
    private static final String METRIC_NAME_ACTIVE_CONNECTIONS = "ActiveConnections";
    private static final String METRIC_NAME_PENDING_CONNECTIONS = "PendingConnections";
    private static final String METRIC_NAME_MAX_CONNECTIONS = "MaxConnections";
    private static final String METRIC_NAME_MIN_CONNECTIONS = "MinConnections";
    private final String poolName;
    private final Timer connectionObtainTimer;
    private final Histogram connectionUsage;
    private final Histogram connectionCreation;
    private final Meter connectionTimeoutMeter;
    private final MetricRegistry registry;

    public KillBillMetricsTracker(final String poolName, final PoolStats poolStats, final MetricRegistry registry) {
        this.poolName = poolName;
        this.registry = registry;
        this.connectionObtainTimer = registry.timer(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_WAIT));
        this.connectionUsage = registry.histogram(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_USAGE));
        this.connectionCreation = registry.histogram(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_CONNECT));
        this.connectionTimeoutMeter = registry.meter(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_TIMEOUT_RATE));

        registry.register(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_TOTAL_CONNECTIONS),
                          (Gauge<Integer>) poolStats::getTotalConnections);

        registry.register(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_IDLE_CONNECTIONS),
                          (Gauge<Integer>) poolStats::getIdleConnections);

        registry.register(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_ACTIVE_CONNECTIONS),
                          (Gauge<Integer>) poolStats::getActiveConnections);

        registry.register(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_PENDING_CONNECTIONS),
                          (Gauge<Integer>) poolStats::getPendingThreads);

        registry.register(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_MAX_CONNECTIONS),
                          (Gauge<Integer>) poolStats::getMaxConnections);

        registry.register(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_MIN_CONNECTIONS),
                          (Gauge<Integer>) poolStats::getMinConnections);
    }

    @Override
    public void close() {
        registry.remove(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_WAIT));
        registry.remove(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_USAGE));
        registry.remove(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_CONNECT));
        registry.remove(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_TIMEOUT_RATE));
        registry.remove(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_TOTAL_CONNECTIONS));
        registry.remove(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_IDLE_CONNECTIONS));
        registry.remove(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_ACTIVE_CONNECTIONS));
        registry.remove(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_PENDING_CONNECTIONS));
        registry.remove(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_MAX_CONNECTIONS));
        registry.remove(String.format("%s.%s.%s", poolName, METRIC_CATEGORY, METRIC_NAME_MIN_CONNECTIONS));
    }

    @Override
    public void recordConnectionAcquiredNanos(final long elapsedAcquiredNanos) {
        connectionObtainTimer.update(elapsedAcquiredNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordConnectionUsageMillis(final long elapsedBorrowedMillis) {
        connectionUsage.update(elapsedBorrowedMillis);
    }

    @Override
    public void recordConnectionTimeout() {
        connectionTimeoutMeter.mark(1);
    }

    @Override
    public void recordConnectionCreatedMillis(final long connectionCreatedMillis) {
        connectionCreation.update(connectionCreatedMillis);
    }

    public Timer getConnectionAcquisitionTimer() {
        return connectionObtainTimer;
    }

    public Histogram getConnectionDurationHistogram() {
        return connectionUsage;
    }

    public Histogram getConnectionCreationHistogram() {
        return connectionCreation;
    }
}
