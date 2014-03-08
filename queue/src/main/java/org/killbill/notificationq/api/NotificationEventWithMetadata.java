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

package org.killbill.notificationq.api;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.bus.api.BusEventWithMetadata;

/**
 * The NotificationEventWithMetadata return to the user. It encapsulates the de-serialized version of the json event on disk.
 *
 * @param <T> The type of event serialized on disk
 */
public class NotificationEventWithMetadata<T extends NotificationEvent> extends BusEventWithMetadata<T> {

    private final UUID futureUserToken;
    private final DateTime effectiveDate;
    private final String queueName;

    public NotificationEventWithMetadata(final Long recordId, final UUID userToken, final DateTime createdDate, final Long searchKey1, final Long searchKey2, final T event,
                                         final UUID futureUserToken, final DateTime effectiveDate, final String queueName) {
        super(recordId, userToken, createdDate, searchKey1, searchKey2, event);
        this.futureUserToken = futureUserToken;
        this.effectiveDate = effectiveDate;
        this.queueName = queueName;
    }

    public UUID getFutureUserToken() {
        return futureUserToken;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public String getQueueName() {
        return queueName;
    }
}
