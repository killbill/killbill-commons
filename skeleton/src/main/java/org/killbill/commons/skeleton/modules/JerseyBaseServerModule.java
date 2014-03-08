/*
 * Copyright 2010-2014 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

public class JerseyBaseServerModule extends BaseServerModule {

    private static final Joiner joiner = Joiner.on(";");

    /**
     * These configuration parameters are normally specified as init-param entries in the web.xml. We have to set
     * them here because GuiceFilter instead has you pass them as arguments to
     * {@link com.google.inject.servlet.ServletModule.ServletKeyBindingBuilder#with(Class, java.util.Map)}.
     *
     * @see com.google.inject.servlet.ServletModule#configureServlets()
     */
    private static final Iterable<String> requestFilterClassNames = ImmutableList.of(
            // The logging filter is still incompatible with the GZIP filter
            //GZIPContentEncodingFilter.class.getName(),
            "com.sun.jersey.api.container.filter.LoggingFilter"
                                                                                    );

    /**
     * These items are <i>intentionally</i> in the reverse order of the above filters. Jersey respects the order in which
     * these items appear, and they should be applied to the response in the reverse order of their application to
     * the request.
     */
    private static final Iterable<String> responseFilterClassNames = ImmutableList.of(
            "com.sun.jersey.api.container.filter.LoggingFilter"
            //GZIPContentEncodingFilter.class.getName()
                                                                                     );

    // See com.sun.jersey.api.core.ResourceConfig
    private final ImmutableMap.Builder<String, String> JERSEY_PARAMS = new ImmutableMap.Builder<String, String>()
            .put("com.sun.jersey.spi.container.ContainerRequestFilters", Joiner.on(';').join(requestFilterClassNames))
            .put("com.sun.jersey.spi.container.ContainerResponseFilters", Joiner.on(';').join(responseFilterClassNames))
                    // The LoggingFilter will log the body by default, which breaks StreamingOutput
            .put("com.sun.jersey.config.feature.logging.DisableEntitylogging", "true");

    public JerseyBaseServerModule(final Map<String, ArrayList<Entry<Class<? extends Filter>, Map<String, String>>>> filters,
                                  final Map<String, ArrayList<Entry<Class<? extends Filter>, Map<String, String>>>> filtersRegex,
                                  final Map<String, Class<? extends HttpServlet>> servlets,
                                  final Map<String, Class<? extends HttpServlet>> servletsRegex,
                                  final Map<String, Class<? extends HttpServlet>> jaxrsServlets,
                                  final Map<String, Class<? extends HttpServlet>> jaxrsServletsRegex,
                                  final String jaxrsUriPattern,
                                  final Collection<String> jaxrsResources) {
        super(filters, filtersRegex, servlets, servletsRegex, jaxrsServlets, jaxrsServletsRegex, jaxrsUriPattern, jaxrsResources);
    }

    @Override
    protected void configureResources() {
        for (final String urlPattern : jaxrsServlets.keySet()) {
            serve(urlPattern).with(jaxrsServlets.get(urlPattern), JERSEY_PARAMS.build());
        }

        for (final String urlPattern : jaxrsServletsRegex.keySet()) {
            serveRegex(urlPattern).with(jaxrsServletsRegex.get(urlPattern), JERSEY_PARAMS.build());
        }

        // Catch-all resources
        if (jaxrsResources.size() != 0) {
            JERSEY_PARAMS.put("com.sun.jersey.config.property.packages", joiner.join(jaxrsResources));
            serveRegex(jaxrsUriPattern).with(GuiceContainer.class, JERSEY_PARAMS.build());
        }
    }
}
