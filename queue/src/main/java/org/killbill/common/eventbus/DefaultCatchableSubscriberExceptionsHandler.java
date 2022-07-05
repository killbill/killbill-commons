/*
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

package org.killbill.common.eventbus;

import java.lang.reflect.InvocationTargetException;

class DefaultCatchableSubscriberExceptionsHandler implements CatchableSubscriberExceptionHandler {

    private final ThreadLocal<Exception> lastException = new ThreadLocal<>() {};

    private final SubscriberExceptionHandler loggerHandler = LoggingHandler.INSTANCE;

    @Override
    public void handleException(final Throwable exception, final SubscriberExceptionContext context) {
        // By convention, re-throw the very first exception
        if (lastException.get() == null) {
            // Wrapping for legacy reasons
            lastException.set(new InvocationTargetException(exception));
        }

        loggerHandler.handleException(exception, context);
    }

    @Override
    public Exception caughtException() {
        final Exception exception = lastException.get();
        reset();
        return exception;
    }

    @Override
    public void reset() {
        lastException.set(null);
    }

}
