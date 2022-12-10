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

import org.killbill.commons.metrics.servlets.HealthCheckServlet;
import org.killbill.commons.metrics.servlets.MetricsServlet;
import org.killbill.commons.metrics.servlets.ThreadDumpServlet;

import com.google.inject.servlet.ServletModule;

public class AdminServletModule extends ServletModule {

    private final String healthcheckUri;
    private final String metricsUri;
    private final String threadsUri;

    public AdminServletModule(final String healthcheckUri,
                              final String metricsUri,
                              final String threadsUri) {
        this.healthcheckUri = healthcheckUri;
        this.metricsUri = metricsUri;
        this.threadsUri = threadsUri;
    }

    @Override
    protected void configureServlets() {
        bind(HealthCheckServlet.class).asEagerSingleton();
        bind(MetricsServlet.class).asEagerSingleton();
        bind(ThreadDumpServlet.class).asEagerSingleton();

        serve(healthcheckUri).with(HealthCheckServlet.class);
        serve(metricsUri).with(MetricsServlet.class);
        serve(threadsUri).with(ThreadDumpServlet.class);
    }
}
