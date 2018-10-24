/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import java.util.UUID;

import org.killbill.billing.rpc.test.queue.gen.EventMsg;
import org.killbill.bus.api.BusEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;

public class TestEvent implements BusEvent {

    private final String source;
    private final String key;
    private final String value;
    private final Long searchKey1;
    private final Long searchKey2;
    private final UUID userToken;

    @JsonCreator
    public TestEvent(@JsonProperty("source") final String source,
                     @JsonProperty("key") final String key,
                     @JsonProperty("value") final String value,
                     @JsonProperty("searchKey1") final Long searchKey1,
                     @JsonProperty("searchKey2") final Long searchKey2,
                     @JsonProperty("userToken") final UUID userToken) {
        this.source = source;
        this.key = key;
        this.value = value;
        this.searchKey1 = searchKey1;
        this.searchKey2 = searchKey2;
        this.userToken = userToken;
    }

    public TestEvent(final EventMsg in) {
        this.source = in.getSource();
        this.key = in.getKey();
        this.value = in.getValue();
        this.searchKey1 = in.getSearchKey1();
        this.searchKey2 = in.getSearchKey2();
        this.userToken = null;// Strings.isNullOrEmpty(in.getUserToken()) ? UUID.randomUUID() : UUID.fromString(in.getUserToken());
    }

    public String getSource() {
        return source;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    @Override
    public Long getSearchKey1() {
        return searchKey1;
    }

    @Override
    public Long getSearchKey2() {
        return searchKey2;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }
}
