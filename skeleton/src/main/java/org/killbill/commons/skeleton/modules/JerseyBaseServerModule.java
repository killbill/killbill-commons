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
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

public class JerseyBaseServerModule extends BaseServerModule {

    private static final Joiner joiner = Joiner.on(";");

    @VisibleForTesting
    static final String JERSEY_PROVIDER_CLASSNAMES = "jersey.config.server.provider.classnames";
    @VisibleForTesting
    static final String JERSEY_LOGGING_VERBOSITY = "jersey.config.logging.verbosity";
    static final String JERSEY_LOGGING_LEVEL = "jersey.config.logging.logger.level";

    protected final Collection<String> jerseyResourcesAndProvidersPackages;
    // See org.glassfish.jersey.servlet.ServletProperties and org.glassfish.jersey.logging.LoggingFeature
    protected final ImmutableMap.Builder<String, String> jerseyParams;

    public JerseyBaseServerModule(final Map<String, ArrayList<Entry<Class<? extends Filter>, Map<String, String>>>> filters,
                                  final Map<String, ArrayList<Entry<Class<? extends Filter>, Map<String, String>>>> filtersRegex,
                                  final Map<String, Class<? extends HttpServlet>> servlets,
                                  final Map<String, Class<? extends HttpServlet>> servletsRegex,
                                  final Map<String, Class<? extends HttpServlet>> jaxrsServlets,
                                  final Map<String, Class<? extends HttpServlet>> jaxrsServletsRegex,
                                  final String jaxrsUriPattern,
                                  final Collection<String> jerseyResourcesAndProvidersPackages,
                                  final Collection<String> jerseyFilters,
                                  final Map<String, String> jerseyParams) {
        super(filters, filtersRegex, servlets, servletsRegex, jaxrsServlets, jaxrsServletsRegex, jaxrsUriPattern);
        this.jerseyResourcesAndProvidersPackages = jerseyResourcesAndProvidersPackages;
        this.jerseyParams = new ImmutableMap.Builder<String, String>();

        String jerseyResourcesAndProvidersClasses = Strings.nullToEmpty(jerseyParams.remove(JERSEY_PROVIDER_CLASSNAMES));
        if (!jerseyFilters.isEmpty()) {
            if (!jerseyResourcesAndProvidersClasses.isEmpty()) {
                jerseyResourcesAndProvidersClasses += ";";
            }
        }
        final String allJerseyResourcesAndProvidersClasses = jerseyResourcesAndProvidersClasses + joiner.join(jerseyFilters);
        if (!allJerseyResourcesAndProvidersClasses.isEmpty()) {
            this.jerseyParams.put(JERSEY_PROVIDER_CLASSNAMES, allJerseyResourcesAndProvidersClasses);
        }

        // The LoggingFilter will log the body by default, which breaks StreamingOutput
        final String jerseyLoggingVerbosity = MoreObjects.firstNonNull(Strings.emptyToNull(jerseyParams.remove(JERSEY_LOGGING_VERBOSITY)), "HEADERS_ONLY");
        final String jerseyLoggingLevel = MoreObjects.firstNonNull(Strings.emptyToNull(jerseyParams.remove(JERSEY_LOGGING_LEVEL)), "INFO");
        this.jerseyParams.put(JERSEY_LOGGING_VERBOSITY, jerseyLoggingVerbosity)
                         .put(JERSEY_LOGGING_LEVEL, jerseyLoggingLevel);

        this.jerseyParams.putAll(jerseyParams);
    }

    @Override
    protected void configureResources() {
        for (final Entry<String, Class<? extends HttpServlet>> entry : jaxrsServlets.entrySet()) {
            serve(entry.getKey()).with(entry.getValue(), jerseyParams.build());
        }

        for (final Entry<String, Class<? extends HttpServlet>> entry : jaxrsServletsRegex.entrySet()) {
            serveRegex(entry.getKey()).with(entry.getValue(), jerseyParams.build());
        }

        // Catch-all resources
        if (!jerseyResourcesAndProvidersPackages.isEmpty()) {
            jerseyParams.put("jersey.config.server.provider.packages", joiner.join(jerseyResourcesAndProvidersPackages));
            serveJaxrsResources();
        }
    }

    protected void serveJaxrsResources() {
        bind(GuiceServletContainer.class).asEagerSingleton();
        serveRegex(jaxrsUriPattern).with(GuiceServletContainer.class, jerseyParams.build());
    }

    @VisibleForTesting
    ImmutableMap.Builder<String, String> getJerseyParams() {
        return jerseyParams;
    }
}
