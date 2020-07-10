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

package org.killbill.commons.skeleton.metrics.health;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheck.Result;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.inject.Inject;

public class KillBillHealthCheckRegistry extends HealthCheckRegistry {

    private static final Logger logger = LoggerFactory.getLogger(KillBillHealthCheckRegistry.class);

    // Lazily register the healthchecks to help Guice with circular dependencies
    @Inject
    public void initialize(final Set<HealthCheck> healthChecks) {
        for (final HealthCheck healthCheck : healthChecks) {
            register(healthCheck.getClass().getName(), healthCheck);
        }
    }

    @Override
    public Result runHealthCheck(final String name) throws NoSuchElementException {
        final Result result = super.runHealthCheck(name);
        logUnHealthyResult(name, result);
        return result;
    }

    @Override
    public SortedMap<String, Result> runHealthChecks() {
        final SortedMap<String, Result> results = super.runHealthChecks();
        logUnHealthyResults(results);
        return results;
    }

    @Override
    public SortedMap<String, Result> runHealthChecks(final ExecutorService executor) {
        final SortedMap<String, Result> results = super.runHealthChecks(executor);
        logUnHealthyResults(results);
        return results;
    }

    private void logUnHealthyResults(final Map<String, Result> results) {
        for (final String healthCheckName : results.keySet()) {
            final Result result = results.get(healthCheckName);
            logUnHealthyResult(healthCheckName, result);
        }
    }

    private void logUnHealthyResult(final String healthCheckName, final Result healthCheckResult) {
        if (!healthCheckResult.isHealthy()) {
            logger.warn("HealthCheck {} failed: {}", healthCheckName, healthCheckResult.toString());
        }
    }
}
