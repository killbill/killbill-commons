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

import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Takes java.util.logging and redirects it into slf4j
 */
public class JULServletContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(final ServletContextEvent event) {
        // We first remove the default handler(s)
        final Logger rootLogger = LogManager.getLogManager().getLogger("");
        final Handler[] handlers = rootLogger.getHandlers();

        if (handlers != null) {
            for (final Handler handler : handlers) {
                rootLogger.removeHandler(handler);
            }
        }

        // Remove all handlers attached to root JUL logger
        removeHandlersForRootLoggerHierarchy();

        // And then we let jul-to-sfl4j do its magic so that jersey messages go to sfl4j
        SLF4JBridgeHandler.install();
    }

    //
    // @See SLF4JBridgeHandler#removeHandlersForRootLogger
    //
    // The issue with SLF4JBridgeHandler#removeHandlersForRootLogger is that in
    // some situations (e.g tomcat deployments), the root logger is *not* actually
    // the root (e.g it has parents which have CONSOLE handlers attached) and we
    // end up duplicating the JUL logs to console and bridge
    //
    private static void removeHandlersForRootLoggerHierarchy() {
        java.util.logging.Logger rootLogger = LogManager.getLogManager().getLogger("");
        while (rootLogger != null) {
            removeHandlersForRootLogger(rootLogger);
            rootLogger = rootLogger.getParent();
        }
    }


    private static void removeHandlersForRootLogger(java.util.logging.Logger rootLogger) {
        java.util.logging.Handler[] handlers = rootLogger.getHandlers();
        for (int i = 0; i < handlers.length; i++) {
            rootLogger.removeHandler(handlers[i]);
        }
    }


    @Override
    public void contextDestroyed(final ServletContextEvent event) {
        SLF4JBridgeHandler.uninstall();
    }
}
