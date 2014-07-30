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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ProfilingData {



    public enum ProfilingDataOutput {
        RAW,
        AGGREGATE,
        ALL
    }

    private final List<ProfilingDataItem> rawData;
    private final ProfilingDataOutput outputType;

    public ProfilingData(final ProfilingDataOutput output) {
        this.outputType = output;
        this.rawData = new ArrayList<ProfilingDataItem>();
    }

    public void merge(@Nullable final ProfilingData otherData) {
        if (otherData == null ||
                otherData.getRawData().isEmpty()) {
            return;
        }
        rawData.addAll(otherData.getRawData());
        Collections.sort(rawData, new Comparator<ProfilingDataItem>() {
            @Override
            public int compare(ProfilingDataItem o1, ProfilingDataItem o2) {
                return o1.getTimestampNsec().compareTo(o2.getTimestampNsec());
            }
        });
    }

    public void addStart(final String key) {
        rawData.add(new ProfilingDataItem(key, LogLineType.START));
    }

    public void addEnd(final String key) {
        rawData.add(new ProfilingDataItem(key, LogLineType.END));
    }

    public List<ProfilingDataItem> getRawData() {
        return rawData;
    }

    public ProfilingDataOutput getOutputType() {
        return outputType;
    }

    public static class ProfilingDataItem {
        private final String key;
        private final Long timestampNsec;
        private final LogLineType lineType;

        private ProfilingDataItem(final String key, final LogLineType lineType) {
            this.key = key;
            this.lineType = lineType;
            this.timestampNsec = System.nanoTime();
        }
        public String getKey() {
            return key;
        }
        public Long getTimestampNsec() {
            return timestampNsec;
        }
        public LogLineType getLineType() {
            return lineType;
        }
    }

    public enum LogLineType {
        START,
        END
    }
}
