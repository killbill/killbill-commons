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

import org.killbill.commons.metrics.api.Counter;

public class KillBillCodahaleCounter implements Counter {

    private final com.codahale.metrics.Counter dwCounter;

    public KillBillCodahaleCounter(final com.codahale.metrics.Counter dwCounter) {
        this.dwCounter = dwCounter;
    }

    @Override
    public long getCount() {
        return dwCounter.getCount();
    }

    @Override
    public void inc(final long n) {
        dwCounter.inc(n);
    }
}
