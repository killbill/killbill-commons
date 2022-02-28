/*
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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.ws.rs.ext.MessageBodyWriter;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.WebConfig;
import org.glassfish.jersey.spi.ExceptionMappers;
import org.jvnet.hk2.guice.bridge.api.GuiceBridge;
import org.jvnet.hk2.guice.bridge.api.GuiceIntoHK2Bridge;
import org.killbill.commons.metrics.api.MetricRegistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;

@Singleton
public class GuiceServletContainer extends ServletContainer {

    private final Injector injector;

    @Inject
    public GuiceServletContainer(final Injector injector) {
        super();
        this.injector = injector;
    }

    @Override
    protected void init(final WebConfig webConfig) throws ServletException {
        // This will instantiate a new instance of ResourceConfig (see WebComponent#createResourceConfig) initialized
        // with the Jersey parameters we specified (see JerseyBaseServerModule)
        super.init(webConfig);

        // HK2 will instantiate the Jersey resources, but will delegate injection of the bindings it doesn't know about to Guice
        final InjectionManager injectionManager = getApplicationHandler().getInjectionManager();
        final ServiceLocator serviceLocator = injectionManager.getInstance(ServiceLocator.class);
        GuiceBridge.getGuiceBridge().initializeGuiceBridge(serviceLocator);
        final GuiceIntoHK2Bridge guiceBridge = serviceLocator.getService(GuiceIntoHK2Bridge.class);
        guiceBridge.bridgeGuiceInjector(injector);

        // Metrics integration
        final String scannedResourcePackagesString = webConfig.getInitParameter(ServerProperties.PROVIDER_PACKAGES);
        if (Strings.emptyToNull(scannedResourcePackagesString) != null) {
            final ImmutableSet<String> scannedResourcePackages = ImmutableSet.<String>copyOf(Splitter.on(CharMatcher.anyOf(" ,;\n"))
                                                                                                     .split(scannedResourcePackagesString));
            ServiceLocatorUtilities.addOneConstant(serviceLocator,
                                                   new TimedInterceptionService(scannedResourcePackages,
                                                                                // From HK2
                                                                                injectionManager.<ExceptionMappers>getInstance(ExceptionMappers.class),
                                                                                // From Guice
                                                                                injector.getInstance(MetricRegistry.class)));
        }

        // Jackson integration
        final boolean hasObjectMapperBinding = !injector.findBindingsByType(TypeLiteral.get(ObjectMapper.class)).isEmpty();
        if (hasObjectMapperBinding) {
            // JacksonJsonProvider is constructed by HK2, but we need to inject the ObjectMapper we constructed via Guice
            final ObjectMapper objectMapper = injector.getInstance(ObjectMapper.class);
            for (final MessageBodyWriter messageBodyWriter : injectionManager.<MessageBodyWriter>getAllInstances(MessageBodyWriter.class)) {
                if (messageBodyWriter instanceof JacksonJsonProvider) {
                    ((JacksonJsonProvider) messageBodyWriter).setMapper(objectMapper);
                    break;
                }
            }
        }
    }
}
