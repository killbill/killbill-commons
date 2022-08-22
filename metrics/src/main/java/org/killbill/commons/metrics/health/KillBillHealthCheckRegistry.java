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

package org.killbill.commons.metrics.health;

import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;

import org.killbill.commons.health.api.HealthCheck;
import org.killbill.commons.health.api.HealthCheckRegistry;
import org.killbill.commons.health.api.Result;
import org.killbill.commons.health.impl.UnhealthyResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KillBillHealthCheckRegistry implements HealthCheckRegistry {

    private static final Logger logger = LoggerFactory.getLogger(KillBillHealthCheckRegistry.class);

    private final ConcurrentMap<String, HealthCheck> healthChecks = new ConcurrentHashMap<>();

    // Lazily register the healthchecks to help Guice with circular dependencies
    @Inject
    public void initialize(final Set<HealthCheck> healthChecks) {
        for (final HealthCheck healthCheck : healthChecks) {
            register(healthCheck.getClass().getName(), healthCheck);
        }
    }

    @Override
    public Set<String> getNames() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(healthChecks.keySet()));
    }

    @Override
    public Result runHealthCheck(final String name) throws NoSuchElementException {
        final HealthCheck healthCheck = healthChecks.get(name);
        if (healthCheck == null) {
            throw new NoSuchElementException("No health check named " + name + " exists");
        }
        final Result result = execute(healthCheck);
        logUnHealthyResult(name, result);
        return result;
    }

    /**
     * Executes the health check, catching and handling any exceptions raised by {@link HealthCheck#check()}.
     *
     * @param healthCheck the healthcheck
     * @return if the component is healthy, a healthy {@link Result}; otherwise, an unhealthy {@link Result} with a descriptive error message or exception
     */
    public Result execute(final HealthCheck healthCheck) {
        try {
            return healthCheck.check();
        } catch (final Exception e) {
            return new UnhealthyResultBuilder().setError(e).createUnhealthyResult();
        }
    }

    @Override
    public void register(final String name, final HealthCheck healthCheck) {
        synchronized (this) {
            if (healthChecks.containsKey(name)) {
                throw new IllegalArgumentException("A health check named " + name + " already exists");
            }
            healthChecks.put(name, healthCheck);
        }
    }

    private void logUnHealthyResult(final String healthCheckName, final Result healthCheckResult) {
        if (!healthCheckResult.isHealthy()) {
            logger.warn("HealthCheck {} failed: {}", healthCheckName, healthCheckResult.toString());
        }
    }
}
