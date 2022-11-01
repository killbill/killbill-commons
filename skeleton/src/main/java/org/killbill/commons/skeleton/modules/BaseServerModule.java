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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;

import com.google.inject.servlet.ServletModule;

public class BaseServerModule extends ServletModule {

    private final Map<String, ArrayList<Map.Entry<Class<? extends Filter>, Map<String, String>>>> filters;
    private final Map<String, ArrayList<Map.Entry<Class<? extends Filter>, Map<String, String>>>> filtersRegex;
    private final Map<String, Class<? extends HttpServlet>> servlets;
    private final Map<String, Class<? extends HttpServlet>> servletsRegex;

    // JAX-RS resources
    final Map<String, Class<? extends HttpServlet>> jaxrsServlets;
    final Map<String, Class<? extends HttpServlet>> jaxrsServletsRegex;
    final String jaxrsUriPattern;

    public BaseServerModule(final Map<String, ArrayList<Entry<Class<? extends Filter>, Map<String, String>>>> filters,
                            final Map<String, ArrayList<Entry<Class<? extends Filter>, Map<String, String>>>> filtersRegex,
                            final Map<String, Class<? extends HttpServlet>> servlets,
                            final Map<String, Class<? extends HttpServlet>> servletsRegex,
                            final Map<String, Class<? extends HttpServlet>> jaxrsServlets,
                            final Map<String, Class<? extends HttpServlet>> jaxrsServletsRegex,
                            final String jaxrsUriPattern) {
        this.filters = new HashMap<>(filters);
        this.filtersRegex = new HashMap<>(filtersRegex);
        this.servlets = new HashMap<>(servlets);
        this.servletsRegex =new HashMap<>(servletsRegex);
        this.jaxrsServlets = new HashMap<>(jaxrsServlets);
        this.jaxrsServletsRegex = new HashMap<>(jaxrsServletsRegex);
        this.jaxrsUriPattern = jaxrsUriPattern;
    }

    @Override
    public void configureServlets() {
        super.configureServlets();

        configureFilters();
        configureFiltersRegex();
        configureRegularServlets();
        configureRegularServletsRegex();
        configureResources();
    }

    protected void configureFilters() {
        for (final String urlPattern : filters.keySet()) {
            for (final Map.Entry<Class<? extends Filter>, Map<String, String>> filter : filters.get(urlPattern)) {
                filter(urlPattern).through(filter.getKey(), filter.getValue());
            }
        }
    }

    protected void configureFiltersRegex() {
        for (final String urlPattern : filtersRegex.keySet()) {
            for (final Map.Entry<Class<? extends Filter>, Map<String, String>> filter : filtersRegex.get(urlPattern)) {
                filterRegex(urlPattern).through(filter.getKey(), filter.getValue());
            }
        }
    }

    protected void configureRegularServlets() {
        for (final String urlPattern : servlets.keySet()) {
            serve(urlPattern).with(servlets.get(urlPattern));
        }
    }

    protected void configureRegularServletsRegex() {
        for (final String urlPattern : servletsRegex.keySet()) {
            serveRegex(urlPattern).with(servletsRegex.get(urlPattern));
        }
    }

    protected void configureResources() {
        for (final String urlPattern : jaxrsServlets.keySet()) {
            serve(urlPattern).with(jaxrsServlets.get(urlPattern));
        }

        for (final String urlPattern : jaxrsServletsRegex.keySet()) {
            serveRegex(urlPattern).with(jaxrsServletsRegex.get(urlPattern));
        }
    }
}
