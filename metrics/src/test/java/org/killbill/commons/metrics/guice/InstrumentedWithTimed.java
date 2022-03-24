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

package org.killbill.commons.metrics.guice;

import org.killbill.commons.metrics.api.annotation.Timed;

@SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
class InstrumentedWithTimed {

    @Timed(name = "things")
    public String doAThing() throws InterruptedException {
        Thread.sleep(10);
        return "poop";
    }

    @Timed
    String doAThingWithDefaultScope() {
        return "defaultResult";
    }

    @Timed
    protected String doAThingWithProtectedScope() {
        return "defaultProtected";
    }

    @Timed(name = "absoluteName", absolute = true)
    protected String doAThingWithAbsoluteName() {
        return "defaultProtected";
    }
}
