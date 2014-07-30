/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.commons.profiling;


public class Profiling<ReturnType> {


    private static final ThreadLocal<ProfilingData> perThreadProfilingData = new ThreadLocal<ProfilingData>();


    public interface WithProfilingCallback<ReturnType> {
        public <ExceptionType extends Throwable> ReturnType execute() throws ExceptionType;
    }

    public ReturnType executeWithProfiling(final String profilingId, final WithProfilingCallback<ReturnType> callback) throws Throwable {
        // Nothing to do
        final ProfilingData profilingData = Profiling.getPerThreadProfilingData();
        if (profilingData == null) {
            return callback.execute();
        }
        profilingData.addStart(profilingId);
        try {
            return callback.execute();
        } finally {
            profilingData.addEnd(profilingId);
        }
    }

    public static ProfilingData getPerThreadProfilingData() {
        return perThreadProfilingData.get();
    }

    public static void setPerThreadProfilingData(final ProfilingData.ProfilingDataOutput output) {
        perThreadProfilingData.set(new ProfilingData(output));
    }

    public static void resetPerThreadProfilingData() {
        perThreadProfilingData.set(null);
    }
}
