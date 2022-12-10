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

package org.killbill.commons.skeleton.listeners;

import java.util.List;

import javax.servlet.ServletContextEvent;

import org.killbill.commons.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;

/**
 * Start up the base module when the server comes up. This gets configured in web.xml:
 * <p/>
 * <pre>
 * &lt;context-param&gt;
 *   &lt;param-name&gt;guiceModuleClassName&lt;/param-name&gt;
 *   &lt;param-value&gt;org.killbill.commons.skeleton.listeners.GuiceServletContextListener&lt;/param-value&gt;
 *  &lt;/context-param&gt;
 * </pre>
 */
public class GuiceServletContextListener extends com.google.inject.servlet.GuiceServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(GuiceServletContextListener.class);

    protected Iterable<? extends Module> guiceModules = null;

    @Override
    public void contextInitialized(final ServletContextEvent event) {
        // Check if the module was overridden in subclasses
        if (guiceModules == null) {
            guiceModules = List.of(initializeGuiceModuleFromWebXML(event));
        }

        super.contextInitialized(event);
    }

    private Module initializeGuiceModuleFromWebXML(final ServletContextEvent event) {
        final String moduleClassName = event.getServletContext().getInitParameter("guiceModuleClassName");
        if (Strings.isNullOrEmpty(moduleClassName)) {
            throw new IllegalStateException("Missing parameter for the base Guice module!");
        }

        try {
            final Class<?> moduleClass = Class.forName(moduleClassName);
            if (!Module.class.isAssignableFrom(moduleClass)) {
                throw new IllegalStateException(String.format("%s exists but is not a Guice Module!", moduleClassName));
            }

            final Module module = (Module) moduleClass.newInstance();
            log.info("Instantiated " + moduleClassName + " as the main guice module.");

            return module;
        } catch (final ClassNotFoundException cnfe) {
            throw new IllegalStateException(cnfe);
        } catch (final InstantiationException ie) {
            throw new IllegalStateException(ie);
        } catch (final IllegalAccessException iae) {
            throw new IllegalStateException(iae);
        }
    }

    /**
     * Do *not* use this method to retrieve the injector. It actually creates a new instance
     * of a Guice injector which in turn will upset Guice.
     */
    @Override
    protected Injector getInjector() {
        if (guiceModules == null) {
            throw new IllegalStateException("Never found the Guice Module to use!");
        }

        return Guice.createInjector(Stage.PRODUCTION, guiceModules);
    }

    /**
     * This method can be called by classes extending GuiceServletContextListener to retrieve
     * the actual injector. This requires some inside knowledge on where it is
     * stored, but the actual key is not visible outside the guice packages.
     */
    public Injector injector(final ServletContextEvent event) {
        return (Injector) event.getServletContext().getAttribute(Injector.class.getName());
    }
}
