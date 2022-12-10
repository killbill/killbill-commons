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

package org.killbill.commons.metrics.dropwizard;

import org.killbill.commons.metrics.api.Meter;

public class KillBillCodahaleMeter implements Meter {

    private final com.codahale.metrics.Meter dwMeter;

    public KillBillCodahaleMeter(final com.codahale.metrics.Meter dwMeter) {
        this.dwMeter = dwMeter;
    }

    @Override
    public long getCount() {
        return dwMeter.getCount();
    }

    @Override
    public void mark(final long n) {
        dwMeter.mark(n);
    }

    @Override
    public double getFifteenMinuteRate() {
        return dwMeter.getFifteenMinuteRate();
    }

    @Override
    public double getFiveMinuteRate() {
        return dwMeter.getFiveMinuteRate();
    }

    @Override
    public double getMeanRate() {
        return dwMeter.getMeanRate();
    }

    @Override
    public double getOneMinuteRate() {
        return dwMeter.getOneMinuteRate();
    }
}
