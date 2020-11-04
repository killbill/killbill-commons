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

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.WebConfig;
import org.jvnet.hk2.guice.bridge.api.GuiceBridge;
import org.jvnet.hk2.guice.bridge.api.GuiceIntoHK2Bridge;

import com.google.inject.Injector;

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
        super.init(webConfig);

        // HK2 will instantiate the Jersey resources, but will delegate injection of the bindings it doesn't know about to Guice
        final ServiceLocator serviceLocator = getApplicationHandler().getInjectionManager().getInstance(ServiceLocator.class);
        GuiceBridge.getGuiceBridge().initializeGuiceBridge(serviceLocator);
        final GuiceIntoHK2Bridge guiceBridge = serviceLocator.getService(GuiceIntoHK2Bridge.class);
        guiceBridge.bridgeGuiceInjector(injector);
    }
}
