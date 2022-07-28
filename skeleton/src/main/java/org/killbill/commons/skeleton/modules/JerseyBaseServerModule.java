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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;

import org.glassfish.jersey.server.ServerProperties;
import org.killbill.commons.utils.Joiner;
import org.killbill.commons.utils.Strings;
import org.killbill.commons.utils.annotation.VisibleForTesting;

public class JerseyBaseServerModule extends BaseServerModule {

    private static final Joiner joiner = Joiner.on(";");

    @VisibleForTesting
    static final String JERSEY_LOGGING_VERBOSITY = "jersey.config.logging.verbosity";
    static final String JERSEY_LOGGING_LEVEL = "jersey.config.logging.logger.level";

    protected final Collection<String> jerseyResourcesAndProvidersPackages;
    protected final Collection<String> jerseyResourcesAndProvidersClasses;
    // See org.glassfish.jersey.servlet.ServletProperties and org.glassfish.jersey.logging.LoggingFeature
    protected final Map<String, String> jerseyParams;

    public JerseyBaseServerModule(final Map<String, ArrayList<Entry<Class<? extends Filter>, Map<String, String>>>> filters,
                                  final Map<String, ArrayList<Entry<Class<? extends Filter>, Map<String, String>>>> filtersRegex,
                                  final Map<String, Class<? extends HttpServlet>> servlets,
                                  final Map<String, Class<? extends HttpServlet>> servletsRegex,
                                  final Map<String, Class<? extends HttpServlet>> jaxrsServlets,
                                  final Map<String, Class<? extends HttpServlet>> jaxrsServletsRegex,
                                  final String jaxrsUriPattern,
                                  final Collection<String> jerseyResourcesAndProvidersPackages,
                                  final Collection<String> jerseyResourcesAndProvidersClasses,
                                  final Map<String, String> jerseyParams) {
        super(filters, filtersRegex, servlets, servletsRegex, jaxrsServlets, jaxrsServletsRegex, jaxrsUriPattern);
        this.jerseyResourcesAndProvidersPackages = jerseyResourcesAndProvidersPackages;
        this.jerseyResourcesAndProvidersClasses = jerseyResourcesAndProvidersClasses;
        this.jerseyParams = new HashMap<>();

        // The LoggingFilter will log the body by default, which breaks StreamingOutput
        final String jerseyLoggingVerbosity = Objects.requireNonNullElse(Strings.emptyToNull(jerseyParams.remove(JERSEY_LOGGING_VERBOSITY)), "HEADERS_ONLY");
        final String jerseyLoggingLevel = Objects.requireNonNullElse(Strings.emptyToNull(jerseyParams.remove(JERSEY_LOGGING_LEVEL)), "INFO");
        this.jerseyParams.put(JERSEY_LOGGING_VERBOSITY, jerseyLoggingVerbosity);
        this.jerseyParams.put(JERSEY_LOGGING_LEVEL, jerseyLoggingLevel);

        this.jerseyParams.putAll(jerseyParams);
    }

    @Override
    protected void configureResources() {
        for (final Entry<String, Class<? extends HttpServlet>> entry : jaxrsServlets.entrySet()) {
            serve(entry.getKey()).with(entry.getValue(), jerseyParams);
        }

        for (final Entry<String, Class<? extends HttpServlet>> entry : jaxrsServletsRegex.entrySet()) {
            serveRegex(entry.getKey()).with(entry.getValue(), jerseyParams);
        }

        // Catch-all resources
        if (!jerseyResourcesAndProvidersPackages.isEmpty()) {
            jerseyParams.put(ServerProperties.PROVIDER_PACKAGES, joiner.join(jerseyResourcesAndProvidersPackages));
            jerseyParams.put(ServerProperties.PROVIDER_CLASSNAMES, joiner.join(jerseyResourcesAndProvidersClasses));
            serveJaxrsResources();
        }
    }

    protected void serveJaxrsResources() {
        bind(GuiceServletContainer.class).asEagerSingleton();
        serveRegex(jaxrsUriPattern).with(GuiceServletContainer.class, jerseyParams);
    }

    @VisibleForTesting
    Map<String, String> getJerseyParams() {
        return jerseyParams;
    }
}
