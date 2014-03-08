/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.commons.concurrent;

import org.slf4j.Logger;

class WrappedRunnable implements Runnable {

    private final Logger log;
    private final Runnable runnable;

    private WrappedRunnable(final Logger log, final Runnable runnable) {
        this.log = log;
        this.runnable = runnable;
    }

    public static Runnable wrap(final Logger log, final Runnable runnable) {
        return runnable instanceof WrappedRunnable ? runnable : new WrappedRunnable(log, runnable);
    }

    @Override
    public void run() {
        final Thread currentThread = Thread.currentThread();

        try {
            runnable.run();
        } catch (Throwable e) {
            log.error(currentThread + " ended abnormally with an exception", e);
        }

        log.debug("{} finished executing", currentThread);
    }
}
