/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.bus.api;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.queue.api.QueueEvent;

/**
 * The BusEventWithMetadata return to the user. It encapsulates the de-serialized version of the json event on disk.
 *
 * @param <T> The type of event serialized on disk
 */
public class BusEventWithMetadata<T extends QueueEvent> {

    private final Long recordId;
    private final UUID userToken;
    private final DateTime createdDate;
    private final Long searchKey1;
    private final Long searchKey2;
    private final T event;

    public BusEventWithMetadata(final Long recordId, final UUID userToken, final DateTime createdDate, final Long searchKey1, final Long searchKey2, final T event) {
        this.recordId = recordId;
        this.userToken = userToken;
        this.createdDate = createdDate;
        this.searchKey1 = searchKey1;
        this.searchKey2 = searchKey2;
        this.event = event;
    }

    public Long getRecordId() {
        return recordId;
    }

    public UUID getUserToken() {
        return userToken;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public Long getSearchKey1() {
        return searchKey1;
    }

    public Long getSearchKey2() {
        return searchKey2;
    }

    public T getEvent() {
        return event;
    }
}
