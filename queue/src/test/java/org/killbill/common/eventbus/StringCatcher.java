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

package org.killbill.common.eventbus;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.killbill.common.eventbus.Subscribe;
import org.testng.Assert;

/**
 * A simple EventSubscriber mock that records Strings.
 *
 * <p>For testing fun, also includes a landmine method that EventBus tests are required <em>not</em>
 * to call ({@link #methodWithoutAnnotation(String)}).
 *
 * @author Cliff Biffle
 */
class StringCatcher {
    private final List<String> events = new ArrayList<>();

    @Subscribe
    public void hereHaveAString(@Nullable final String string) {
        events.add(string);
    }

    public void methodWithoutAnnotation(@Nullable final String string) {
        Assert.fail("Event bus must not call methods without @Subscribe: " + string);
    }

    public List<String> getEvents() {
        return events;
    }
}