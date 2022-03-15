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

package org.killbill.commons.metrics.servlets;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.killbill.commons.health.api.HealthCheckRegistry;
import org.killbill.commons.health.api.Result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class HealthCheckServlet extends HttpServlet {

    public static final String HEALTH_CHECK_REGISTRY = HealthCheckServlet.class.getCanonicalName() + ".registry";
    public static final String HEALTH_CHECK_MAPPER = HealthCheckServlet.class.getCanonicalName() + ".mapper";

    private static final long serialVersionUID = -128753347944002146L;
    private static final String CONTENT_TYPE = "application/json";

    private transient HealthCheckRegistry registry;
    private transient ObjectMapper mapper;

    public HealthCheckServlet() {
    }

    public HealthCheckServlet(final HealthCheckRegistry registry) {
        this.registry = registry;
    }

    private static boolean isAllHealthy(final Map<String, Result> results) {
        for (final Result result : results.values()) {
            if (!result.isHealthy()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);

        final ServletContext context = config.getServletContext();
        if (null == registry) {
            final Object registryAttr = context.getAttribute(HEALTH_CHECK_REGISTRY);
            if (registryAttr instanceof HealthCheckRegistry) {
                this.registry = (HealthCheckRegistry) registryAttr;
            } else {
                throw new ServletException("Couldn't find a HealthCheckRegistry instance.");
            }
        }

        final Object mapperAttr = context.getAttribute(HEALTH_CHECK_MAPPER);
        if (mapperAttr instanceof ObjectMapper) {
            this.mapper = (ObjectMapper) mapperAttr;
        } else {
            this.mapper = new ObjectMapper();
        }

        this.mapper.registerModule(new HealthCheckJacksonModule());
    }

    @Override
    public void destroy() {
        super.destroy();
    }

    @Override
    protected void doGet(final HttpServletRequest req,
                         final HttpServletResponse resp) throws IOException {
        final SortedMap<String, Result> results = runHealthChecks();
        resp.setContentType(CONTENT_TYPE);
        resp.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");
        if (results.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        } else {
            if (isAllHealthy(results)) {
                resp.setStatus(HttpServletResponse.SC_OK);
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

        try (final OutputStream output = resp.getOutputStream()) {
            getWriter(req).writeValue(output, results);
        }
    }

    private ObjectWriter getWriter(final ServletRequest request) {
        final boolean prettyPrint = Boolean.parseBoolean(request.getParameter("pretty"));
        if (prettyPrint) {
            return mapper.writerWithDefaultPrettyPrinter();
        }
        return mapper.writer();
    }

    private SortedMap<String, Result> runHealthChecks() {
        final SortedMap<String, Result> results = new TreeMap<>();
        for (final String name : registry.getNames()) {
            results.put(name, registry.runHealthCheck(name));
        }
        return results;
    }
}
