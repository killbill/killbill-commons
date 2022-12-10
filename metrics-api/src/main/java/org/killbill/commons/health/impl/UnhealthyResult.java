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

package org.killbill.commons.health.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.killbill.commons.health.api.Result;

public class UnhealthyResult implements Result {

    private final String message;
    private final Throwable error;
    private final long time;
    private final Map<String, Object> details;

    public UnhealthyResult(final String message,
                           final Throwable error,
                           final long time,
                           final Map<String, Object> details) {
        this.message = message;
        this.error = new Throwable(error);
        this.time = time;
        this.details = details == null ? Collections.emptyMap() : new HashMap<>(details);
    }

    @Override
    public boolean isHealthy() {
        return false;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Throwable getError() {
        return new Throwable(error);
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public Map<String, Object> getDetails() {
        return new HashMap<>(details);
    }

    @Override
    public String toString() {
        return "UnhealthyResult{" + "message='" + message + '\'' +
               ", error=" + error +
               ", time=" + time +
               ", details=" + details +
               '}';
    }
}
