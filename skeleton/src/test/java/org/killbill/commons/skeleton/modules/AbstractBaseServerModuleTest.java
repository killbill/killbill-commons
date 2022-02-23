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

import java.io.IOException;
import java.net.ServerSocket;

import javax.servlet.ServletContextEvent;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.testng.Assert;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.servlet.InstrumentedFilter;
import com.codahale.metrics.servlets.AdminServlet;
import com.codahale.metrics.servlets.HealthCheckServlet;
import com.codahale.metrics.servlets.MetricsServlet;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;

public abstract class AbstractBaseServerModuleTest {

    final String metricsUri = "/1.0/metrics";
    final String pingUri = "/1.0/ping";
    final String threadsUri = "/1.0/threads";
    final String healthCheckUri = "/1.0/healthcheck";

    protected Server startServer(final Module... modules) throws Exception {
        final Injector injector = Guice.createInjector(modules);
        return startServer(injector);
    }

    protected Server startServer(final Injector injector) throws Exception {
        final Server server = new Server(getPort());
        final ServletContextHandler servletContextHandler = new ServletContextHandler();
        servletContextHandler.addEventListener(new GuiceServletContextListener() {
            @Override
            protected Injector getInjector() {
                return injector;
            }

            @Override
            public void contextInitialized(final ServletContextEvent servletContextEvent) {
                super.contextInitialized(servletContextEvent);

                // For Kill Bill, this is done in KillbillPlatformGuiceListener
                final MetricRegistry metricRegistry = injector.getInstance(MetricRegistry.class);
                servletContextEvent.getServletContext().setAttribute(HealthCheckServlet.HEALTH_CHECK_REGISTRY, injector.getInstance(HealthCheckRegistry.class));
                servletContextEvent.getServletContext().setAttribute(MetricsServlet.METRICS_REGISTRY, metricRegistry);
                servletContextEvent.getServletContext().setAttribute(InstrumentedFilter.REGISTRY_ATTRIBUTE, metricRegistry);
                servletContextEvent.getServletContext().setInitParameter(AdminServlet.METRICS_URI_PARAM_KEY, metricsUri);
                servletContextEvent.getServletContext().setInitParameter(AdminServlet.PING_URI_PARAM_KEY, pingUri);
                servletContextEvent.getServletContext().setInitParameter(AdminServlet.THREADS_URI_PARAM_KEY, threadsUri);
                servletContextEvent.getServletContext().setInitParameter(AdminServlet.HEALTHCHECK_URI_PARAM_KEY, healthCheckUri);
            }
        });

        servletContextHandler.addFilter(GuiceFilter.class, "/*", null);
        servletContextHandler.addServlet(DefaultServlet.class, "/*");
        server.setHandler(servletContextHandler);
        server.start();

        final Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    server.join();
                } catch (final InterruptedException ignored) {
                }
            }
        };
        t.setDaemon(true);
        t.start();
        Assert.assertTrue(server.isRunning());
        return server;
    }

    private int getPort() {
        final int port;
        try {
            final ServerSocket socket = new ServerSocket(0);
            port = socket.getLocalPort();
            socket.close();
        } catch (final IOException e) {
            Assert.fail();
            return -1;
        }

        return port;
    }
}
