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
import java.net.URI;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import com.google.inject.Injector;
import com.sun.jersey.api.container.ContainerException;
import com.sun.jersey.api.uri.UriComponent;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

import static javax.ws.rs.core.UriBuilder.fromUri;

@Singleton
public class Jersey2CompatGuiceContainer extends GuiceContainer {

    @Inject
    public Jersey2CompatGuiceContainer(final Injector injector) {
        super(injector);
    }

    /**
     * Dispatches client requests to the {@link #service(java.net.URI, java.net.URI, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)  }
     * method.
     *
     * @param request  the {@link HttpServletRequest} object that
     *                 contains the request the client made to
     *                 the servlet.
     * @param response the {@link HttpServletResponse} object that
     *                 contains the response the servlet returns
     *                 to the client.
     * @throws IOException      if an input or output error occurs
     *                          while the servlet is handling the
     *                          HTTP request.
     * @throws ServletException if the HTTP request cannot
     *                          be handled.
     */
    @Override
    public void service(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        /**
         * There is an annoying edge case where the service method is
         * invoked for the case when the URI is equal to the deployment URL
         * minus the '/', for example http://locahost:8080/HelloWorldWebApp
         */
        final String servletPath = request.getServletPath();
        final String pathInfo = request.getPathInfo();
        final StringBuffer requestURL = request.getRequestURL();
        String requestURI = request.getRequestURI();
        final boolean checkPathInfo = pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/");

        /**
         * The HttpServletRequest.getRequestURL() contains the complete URI
         * minus the query and fragment components.
         */
        final UriBuilder absoluteUriBuilder;
        try {
            absoluteUriBuilder = fromUriJersey2Compat(requestURL.toString());
        } catch (final IllegalArgumentException iae) {
            final Response.Status badRequest = Response.Status.BAD_REQUEST;
            response.sendError(badRequest.getStatusCode(), badRequest.getReasonPhrase());
            return;
        }

        if (checkPathInfo && !request.getRequestURI().endsWith("/")) {
            // Only do this if the last segment of the servlet path does not contain '.'
            // This handles the case when the extension mapping is used with the servlet
            // see issue 506
            // This solution does not require parsing the deployment descriptor,
            // however still leaves it broken for the very rare case if a standard path
            // servlet mapping would include dot in the last segment (e.g. /.webresources/*)
            // and somebody would want to hit the root resource without the trailing slash
            final int i = servletPath.lastIndexOf("/");
            if (servletPath.substring(i + 1).indexOf('.') < 0) {
                // PIERRE webComponent is private
                /*
                if (webComponent.getResourceConfig().getFeature(ResourceConfig.FEATURE_REDIRECT)) {
                    URI l = absoluteUriBuilder.
                                                      path("/").
                                                      replaceQuery(request.getQueryString()).build();

                    response.setStatus(307);
                    response.setHeader("Location", l.toASCIIString());
                    return;
                } else {
                    requestURL.append("/");
                    requestURI += "/";
                }
                */
                requestURL.append("/");
                requestURI += "/";
            }
        }

        /**
         * The HttpServletRequest.getPathInfo() and
         * HttpServletRequest.getServletPath() are in decoded form.
         *
         * On some servlet implementations the getPathInfo() removed
         * contiguous '/' characters. This is problematic if URIs
         * are embedded, for example as the last path segment.
         * We need to work around this and not use getPathInfo
         * for the decodedPath.
         */
        final String decodedBasePath = request.getContextPath() + servletPath + "/";

        final String encodedBasePath = UriComponent.encode(decodedBasePath,
                                                           UriComponent.Type.PATH);

        if (!decodedBasePath.equals(encodedBasePath)) {
            throw new ContainerException("The servlet context path and/or the " +
                                         "servlet path contain characters that are percent encoded");
        }

        final URI baseUri = absoluteUriBuilder.replacePath(encodedBasePath).
                build();

        String queryParameters = request.getQueryString();
        if (queryParameters == null) {
            queryParameters = "";
        }

        final URI requestUri = absoluteUriBuilder.replacePath(requestURI).
                replaceQuery(queryParameters).
                                                         build();

        service(baseUri, requestUri, request, response);
    }

    public static UriBuilder fromUriJersey2Compat(final String uri) throws IllegalArgumentException {
        final URI u;
        try {
            u = URI.create(uri);
        } catch (final NullPointerException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
        return fromUri(u);
    }
}
