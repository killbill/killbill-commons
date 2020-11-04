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
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;

import com.google.common.collect.Maps;
import com.google.inject.Module;

public class BaseServerModuleBuilder {

    public enum JaxrsImplementation {
        NONE,
        JERSEY
    }

    // By default, proxy all requests to the Guice/Jax-RS servlet
    private String jaxrsUriPattern = "/.*";
    private final List<String> jaxrsResources = new ArrayList<String>();
    private final List<Module> modules = new ArrayList<Module>();
    private final Map<String, ArrayList<Map.Entry<Class<? extends Filter>, Map<String, String>>>> filters = new HashMap<String, ArrayList<Map.Entry<Class<? extends Filter>, Map<String, String>>>>();
    private final Map<String, ArrayList<Map.Entry<Class<? extends Filter>, Map<String, String>>>> filtersRegex = new HashMap<String, ArrayList<Map.Entry<Class<? extends Filter>, Map<String, String>>>>();
    private final Map<String, Class<? extends HttpServlet>> jaxrsServlets = new HashMap<String, Class<? extends HttpServlet>>();
    private final Map<String, Class<? extends HttpServlet>> jaxrsServletsRegex = new HashMap<String, Class<? extends HttpServlet>>();
    private final Map<String, Class<? extends HttpServlet>> servlets = new HashMap<String, Class<? extends HttpServlet>>();
    private final Map<String, Class<? extends HttpServlet>> servletsRegex = new HashMap<String, Class<? extends HttpServlet>>();

    // Specific filters called by jersey
    private final List<String> jerseyFilters = new ArrayList<String>();
    private final Map<String, String> jerseyParams = new HashMap<String, String>();

    private JaxrsImplementation jaxrsImplementation = JaxrsImplementation.JERSEY;

    public BaseServerModuleBuilder() {
    }

    public BaseServerModuleBuilder addFilter(final String urlPattern, final Class<? extends Filter> filterKey) {
        return addFilter(urlPattern, filterKey, new HashMap<String, String>());
    }

    public BaseServerModuleBuilder addFilter(final String urlPattern, final Class<? extends Filter> filterKey, final Map<String, String> initParams) {
        if (this.filters.get(urlPattern) == null) {
            this.filters.put(urlPattern, new ArrayList<Map.Entry<Class<? extends Filter>, Map<String, String>>>());
        }

        this.filters.get(urlPattern).add(Maps.<Class<? extends Filter>, Map<String, String>>immutableEntry(filterKey, initParams));
        return this;
    }

    public BaseServerModuleBuilder addFilterRegex(final String urlPattern, final Class<? extends Filter> filterKey) {
        return addFilterRegex(urlPattern, filterKey, new HashMap<String, String>());
    }

    public BaseServerModuleBuilder addFilterRegex(final String urlPattern, final Class<? extends Filter> filterKey, final Map<String, String> initParams) {
        if (this.filtersRegex.get(urlPattern) == null) {
            this.filtersRegex.put(urlPattern, new ArrayList<Map.Entry<Class<? extends Filter>, Map<String, String>>>());
        }

        this.filtersRegex.get(urlPattern).add(Maps.<Class<? extends Filter>, Map<String, String>>immutableEntry(filterKey, initParams));
        return this;
    }

    public BaseServerModuleBuilder addServlet(final String urlPattern, final Class<? extends HttpServlet> filterKey) {
        this.servlets.put(urlPattern, filterKey);
        return this;
    }

    public BaseServerModuleBuilder addServletRegex(final String urlPattern, final Class<? extends HttpServlet> filterKey) {
        this.servletsRegex.put(urlPattern, filterKey);
        return this;
    }

    public BaseServerModuleBuilder addJaxrsServlet(final String urlPattern, final Class<? extends HttpServlet> filterKey) {
        this.jaxrsServlets.put(urlPattern, filterKey);
        return this;
    }

    public BaseServerModuleBuilder addJaxrsServletRegex(final String urlPattern, final Class<? extends HttpServlet> filterKey) {
        this.jaxrsServletsRegex.put(urlPattern, filterKey);
        return this;
    }

    public BaseServerModuleBuilder addJerseyFilter(final String filterClassName) {
        this.jerseyFilters.add(filterClassName);
        return this;
    }

    public BaseServerModuleBuilder addJerseyParam(final String key, final String value) {
        this.jerseyParams.put(key, value);
        return this;
    }

    /**
     * Specify the Uri pattern to use for the Guice/Jersey servlet
     *
     * @param jaxrsUriPattern Any Java-style regular expression
     * @return the current module builder
     * @see BaseServerModuleBuilder#addJaxrsResource
     */
    public BaseServerModuleBuilder setJaxrsUriPattern(final String jaxrsUriPattern) {
        this.jaxrsUriPattern = jaxrsUriPattern;
        return this;
    }

    /**
     * Add a package to be scanned for the Guice/Jersey servlet
     *
     * @param resource package to scan
     * @return the current module builder
     */
    public BaseServerModuleBuilder addJaxrsResource(final String resource) {
        this.jaxrsResources.add(resource);
        return this;
    }

    public BaseServerModuleBuilder setJaxrsImplementation(final JaxrsImplementation jaxrsImplementation) {
        this.jaxrsImplementation = jaxrsImplementation;
        return this;
    }

    public BaseServerModule build() {
        switch (jaxrsImplementation) {
            case NONE:
                return new BaseServerModule(filters,
                                            filtersRegex,
                                            servlets,
                                            servletsRegex,
                                            jaxrsServlets,
                                            jaxrsServletsRegex,
                                            jaxrsUriPattern,
                                            jaxrsResources);
            case JERSEY:
                return new JerseyBaseServerModule(filters,
                                                  filtersRegex,
                                                  servlets,
                                                  servletsRegex,
                                                  jaxrsServlets,
                                                  jaxrsServletsRegex,
                                                  jaxrsUriPattern,
                                                  jaxrsResources,
                                                  jerseyFilters,
                                                  jerseyParams);
            default:
                throw new IllegalArgumentException();
        }
    }
}
