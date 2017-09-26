/*
 * Copyright 2017 Groupon, Inc
 * Copyright 2017 The Billing Project, LLC
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

import java.util.HashMap;
import java.util.Map;

import com.codahale.metrics.servlets.AdminServlet;
import com.google.inject.servlet.ServletModule;

/**
 * A guice servlet module that registers the {@link AdminServlet} via guice and also configures all healthchecks bound
 * via guice to it.
 * <p>
 * To use, install this module in your servlet module (or add as a separate module), and bind the health checks via a
 * multi binder:
 * <pre>
 * <code>install(new AdminServletModule());
 *
 * Multibinder&lt;HealthCheck&gt; healthChecksBinder = Multibinder.newSetBinder(binder(), HealthCheck.class);
 *
 * healthChecksBinder.addBinding().to(MyCoolHealthCheck.class);
 * healthChecksBinder.addBinding().to(MyOtherCoolHealthCheck.class);
 * </code>
 * </pre>
 * The module offers the same overloaded constructors to specify the uris for the healthcheck, metrics, etc. E.g.
 * <pre>
 * <code>install(new AdminServletModule("/1.0/healthcheck", "/1.0/metrics", "/1.0/ping", "/1.0/threads"));
 * </code>
 * </pre>
 * In order to use this module, you need the <code>guice-servlet</code> and <code>guice-multibindings</code>
 * dependencies in addition to the normal <code>guice</code> dependency:
 * <pre>
 * {@code <dependency>
 *   <groupId>com.google.inject</groupId>
 *   <artifactId>guice</artifactId>
 *   <version>3.0</version>
 * </dependency>
 * <dependency>
 *   <groupId>com.google.inject.extensions</groupId>
 *   <artifactId>guice-servlet</artifactId>
 *   <version>3.0</version>
 * </dependency>
 * <dependency>
 *   <groupId>com.google.inject.extensions</groupId>
 *   <artifactId>guice-multibindings</artifactId>
 *   <version>3.0</version>
 * </dependency>
 * }
 * </pre>
 * <p>
 * Inspired from https://github.com/palominolabs/metrics-guice-servlet
 */
public class AdminServletModule extends ServletModule {

    private final String healthcheckUri;
    private final String metricsUri;
    private final String pingUri;
    private final String threadsUri;

    public AdminServletModule() {
        this(AdminServlet.DEFAULT_HEALTHCHECK_URI, AdminServlet.DEFAULT_METRICS_URI,
             AdminServlet.DEFAULT_PING_URI, AdminServlet.DEFAULT_THREADS_URI);
    }

    public AdminServletModule(final String healthcheckUri, final String metricsUri, final String pingUri, final String threadsUri) {
        this.healthcheckUri = healthcheckUri;
        this.metricsUri = metricsUri;
        this.pingUri = pingUri;
        this.threadsUri = threadsUri;
    }

    @Override
    protected void configureServlets() {
        bind(AdminServlet.class).asEagerSingleton();

        final Map<String, String> initParams = new HashMap<String, String>();
        initParams.put("metrics-uri", metricsUri);
        initParams.put("ping-uri", pingUri);
        initParams.put("threads-uri", threadsUri);
        initParams.put("healthcheck-uri", healthcheckUri);

        serve(healthcheckUri, metricsUri, pingUri, threadsUri).with(AdminServlet.class, initParams);
    }
}
