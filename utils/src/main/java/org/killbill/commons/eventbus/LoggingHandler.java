/*
 * Copyright (C) 2007 The Guava Authors
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.commons.eventbus;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class originally exist as nested static class in {@link EventBus} as default {@link SubscriberExceptionHandler}
 * implementation.
 */
class LoggingHandler implements SubscriberExceptionHandler {

    static final SubscriberExceptionHandler INSTANCE = new LoggingHandler();

    private static Logger logger(final SubscriberExceptionContext context) {
        return Logger.getLogger(EventBus.class.getName() + "." + context.getEventBus().identifier());
    }

    private static String message(final SubscriberExceptionContext context) {
        final Method method = context.getSubscriberMethod();
        return "Exception thrown by subscriber method "
               + method.getName()
               + '('
               + method.getParameterTypes()[0].getName()
               + ')'
               + " on subscriber "
               + context.getSubscriber()
               + " when dispatching event: "
               + context.getEvent();
    }

    @Override
    public void handleException(final Throwable exception, final SubscriberExceptionContext context) {
        final Logger logger = logger(context);
        if (logger.isLoggable(Level.SEVERE)) {
            logger.log(Level.SEVERE, message(context), exception);
        }
    }
}
