/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;

public class Jersey2CompatServerModule extends JerseyBaseServerModule {

    public Jersey2CompatServerModule(final Map<String, ArrayList<Entry<Class<? extends Filter>, Map<String, String>>>> filters,
                                     final Map<String, ArrayList<Entry<Class<? extends Filter>, Map<String, String>>>> filtersRegex,
                                     final Map<String, Class<? extends HttpServlet>> servlets,
                                     final Map<String, Class<? extends HttpServlet>> servletsRegex,
                                     final Map<String, Class<? extends HttpServlet>> jaxrsServlets,
                                     final Map<String, Class<? extends HttpServlet>> jaxrsServletsRegex,
                                     final String jaxrsUriPattern,
                                     final Collection<String> jaxrsResources,
                                     final List<String> jerseyFilters,
                                     final Map<String, String> jerseyParams) {
        super(filters, filtersRegex, servlets, servletsRegex, jaxrsServlets, jaxrsServletsRegex, jaxrsUriPattern, jaxrsResources, jerseyFilters, jerseyParams);
    }

    @Override
    protected void serveJaxrsResources() {
        serveRegex(jaxrsUriPattern).with(Jersey2CompatGuiceContainer.class, jerseyParams.build());
    }
}
