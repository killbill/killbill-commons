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

package org.killbill.commons.profiling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class ProfilingData {

    private final List<ProfilingDataItem> rawData;
    private final ProfilingFeature profileFeature;

    public ProfilingData(final ProfilingFeature profileFeature) {
        this.profileFeature = profileFeature;
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
            public int compare(final ProfilingDataItem o1, final ProfilingDataItem o2) {
                return o1.getTimestampNsec().compareTo(o2.getTimestampNsec());
            }
        });
    }

    public void addStart(final ProfilingFeature.ProfilingFeatureType profileType, final String id) {
        rawData.add(new ProfilingDataItem(profileType, id, LogLineType.START));
    }

    public void addEnd(final ProfilingFeature.ProfilingFeatureType profileType, final String id) {
        rawData.add(new ProfilingDataItem(profileType, id, LogLineType.END));
    }

    public List<ProfilingDataItem> getRawData() {
        if (rawData == null || rawData.isEmpty()) {
            return Collections.emptyList();
        }
        return Lists.newArrayList(Iterables.filter(rawData, new Predicate<ProfilingDataItem>() {
            @Override
            public boolean apply(final ProfilingDataItem input) {
                return input != null && profileFeature.isDefined(input.getProfileType());
            }
        }));
    }

    public ProfilingFeature getProfileFeature() {
        return profileFeature;
    }

    public static class ProfilingDataItem {
        private final ProfilingFeature.ProfilingFeatureType profileType;
        private final String id;
        private final Long timestampNsec;
        private final LogLineType lineType;

        private ProfilingDataItem(final ProfilingFeature.ProfilingFeatureType profileType, final String id, final LogLineType lineType) {
            this.profileType = profileType;
            this.id = id;
            this.lineType = lineType;
            this.timestampNsec = System.nanoTime();
        }

        public ProfilingFeature.ProfilingFeatureType getProfileType() {
            return profileType;
        }

        public String getKey() {
            return profileType + ":" + id;
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
