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

import java.util.concurrent.TimeUnit;

import org.killbill.commons.metrics.api.Snapshot;
import org.killbill.commons.metrics.api.Timer;

public class KillBillCodahaleTimer implements Timer {

    private final com.codahale.metrics.Timer dwTimer;

    public KillBillCodahaleTimer(final com.codahale.metrics.Timer dwTimer) {
        this.dwTimer = dwTimer;
    }

    @Override
    public long getCount() {
        return dwTimer.getCount();
    }

    @Override
    public void update(final long duration, final TimeUnit unit) {
        dwTimer.update(duration, unit);
    }

    @Override
    public double getFifteenMinuteRate() {
        return dwTimer.getFifteenMinuteRate();
    }

    @Override
    public double getFiveMinuteRate() {
        return dwTimer.getFiveMinuteRate();
    }

    @Override
    public double getMeanRate() {
        return dwTimer.getMeanRate();
    }

    @Override
    public double getOneMinuteRate() {
        return dwTimer.getOneMinuteRate();
    }

    @Override
    public Snapshot getSnapshot() {
        return new KillBillCodahaleSnapshot(dwTimer.getSnapshot());
    }
}
