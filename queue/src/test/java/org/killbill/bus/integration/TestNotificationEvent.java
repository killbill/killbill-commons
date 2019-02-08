/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.bus.integration;

import org.killbill.billing.rpc.test.queue.gen.EventMsg;
import org.killbill.notificationq.api.NotificationEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TestNotificationEvent implements NotificationEvent {

    private final String source;

    @JsonCreator
    public TestNotificationEvent(@JsonProperty("source") final String source) {
        this.source = source;
    }

    public TestNotificationEvent(final EventMsg in, final String testName) {
        this.source = in.getSource() != null ? in.getSource() : testName;
    }

    public String getSource() {
        return source;
    }

}
