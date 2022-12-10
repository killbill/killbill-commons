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

package org.killbill.commons.health.api;

import java.util.NoSuchElementException;
import java.util.Set;

public interface HealthCheckRegistry {

    /**
     * Returns a set of the names of all registered health checks.
     *
     * @return the names of all registered health checks
     */
    Set<String> getNames();

    /**
     * Runs the health check with the given name.
     *
     * @param name the health check's name
     * @return the result of the health check
     * @throws NoSuchElementException if there is no health check with the given name
     */
    Result runHealthCheck(String name) throws NoSuchElementException;

    /**
     * Registers an application {@link HealthCheck}.
     *
     * @param name        the name of the health check
     * @param healthCheck the {@link HealthCheck} instance
     */
    void register(String name, HealthCheck healthCheck);
}
