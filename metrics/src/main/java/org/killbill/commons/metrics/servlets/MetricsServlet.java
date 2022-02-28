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

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.impl.NoOpMetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class MetricsServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(MetricsServlet.class);

    public static final String METRICS_REGISTRY = MetricsServlet.class.getCanonicalName() + ".registry";
    public static final String OBJECT_MAPPER = MetricsServlet.class.getCanonicalName() + ".mapper";

    private static final long serialVersionUID = 5368376475901310760L;
    private static final String CONTENT_TYPE = "application/json";

    protected String allowedOrigin;
    protected transient MetricRegistry registry;
    protected transient ObjectMapper mapper;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);

        final ServletContext context = config.getServletContext();
        if (null == registry) {
            final Object registryAttr = context.getAttribute(METRICS_REGISTRY);
            if (registryAttr instanceof MetricRegistry) {
                if (registryAttr instanceof NoOpMetricRegistry) {
                    logger.warn("Metrics are not enabled");
                } else {
                    this.registry = (MetricRegistry) registryAttr;
                }
            } else {
                throw new ServletException("Couldn't find a MetricRegistry instance.");
            }
        }
        if (null == mapper) {
            final Object mapperAttr = context.getAttribute(OBJECT_MAPPER);
            if (mapperAttr instanceof ObjectMapper) {
                this.mapper = (ObjectMapper) mapperAttr;
            } else {
                this.mapper = new ObjectMapper();
            }
        }
    }

    @Override
    protected void doGet(final HttpServletRequest req,
                         final HttpServletResponse resp) throws IOException {
        resp.setContentType(CONTENT_TYPE);
        if (allowedOrigin != null) {
            resp.setHeader("Access-Control-Allow-Origin", allowedOrigin);
        }
        resp.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");
        resp.setStatus(HttpServletResponse.SC_OK);

        try (final OutputStream output = resp.getOutputStream()) {
            getWriter(req).writeValue(output, registry);
        }
    }

    protected ObjectWriter getWriter(final ServletRequest request) {
        final boolean prettyPrint = Boolean.parseBoolean(request.getParameter("pretty"));
        if (prettyPrint) {
            return mapper.writerWithDefaultPrettyPrinter();
        }
        return mapper.writer();
    }
}
