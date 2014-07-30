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




import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ProfilingData {

    public enum ProfilingDataOutput {
        RAW,
        AGGREGATE,
        ALL
    }

    private final Map<String, List<Long>> rawData;
    private final ProfilingDataOutput outputType;

    public ProfilingData(final ProfilingDataOutput output) {
        this.outputType = output;
        this.rawData = new HashMap<String, List<Long>>();
    }

    public void merge(@Nullable final ProfilingData otherData) {
        if (otherData == null ||
                otherData.getRawData().isEmpty()) {
            return;
        }
        rawData.putAll(otherData.getRawData());
    }

    public void add(final String profilingId, final long timeUsec) {
        List<Long> values = rawData.get(profilingId);
        if (values == null) {
            values = new LinkedList<Long>();
            rawData.put(profilingId, values);
        }
        values.add(timeUsec);
    }

    public Map<String, List<Long>> getRawData() {
        return rawData;
    }

    public ProfilingDataOutput getOutputType() {
        return outputType;
    }
}
