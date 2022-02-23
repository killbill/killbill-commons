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

package com.palominolabs.metrics.guice;

import com.codahale.metrics.annotation.Metered;

@SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
class InstrumentedWithMetered {

    @Metered(name = "things")
    public String doAThing() {
        return "poop";
    }

    @Metered
    String doAThingWithDefaultScope() {
        return "defaultResult";
    }

    @Metered
    protected String doAThingWithProtectedScope() {
        return "defaultProtected";
    }

    @Metered(name = "n")
    protected String doAThingWithName() {
        return "withName";
    }

    @Metered(name = "nameAbs", absolute = true)
    protected String doAThingWithAbsoluteName() {
        return "absoluteName";
    }
}
