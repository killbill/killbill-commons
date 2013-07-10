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

package com.ning.billing.notificationq.dao;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.bus.dao.BusEventModelDao;
import com.ning.billing.queue.api.PersistentQueueEntryLifecycleState;

public class NotificationEventModelDao extends BusEventModelDao {


    private UUID futureUserToken;
    private DateTime effectiveDate;
    private String queueName;

    public NotificationEventModelDao() { /* Default ctor for jdbi mapper */ }

    public NotificationEventModelDao(final long id, final String createdOwner, final String owner, final DateTime createdDate, final DateTime nextAvailable, final PersistentQueueEntryLifecycleState processingState,
                                     final String eventJsonClass, final String eventJson, final UUID userToken, final Long searchKey1, final Long searchKey2, final UUID futureUserToken, final DateTime effectiveDate, final String queueName) {
        super(id, createdOwner, owner, createdDate, nextAvailable, processingState, eventJsonClass, eventJson, userToken, searchKey1, searchKey2);
        this.futureUserToken = futureUserToken;
        this.effectiveDate = effectiveDate;
        this.queueName = queueName;
    }

    public NotificationEventModelDao(final String createdOwner, final DateTime createdDate, final String eventJsonClass,
                                     final String eventJson, final UUID userToken, final Long searchKey1, final Long searchKey2, final UUID futureUserToken, final DateTime effectiveDate, final String queueName) {
        this(-1L, createdOwner, null, createdDate, null, PersistentQueueEntryLifecycleState.AVAILABLE,
             eventJsonClass, eventJson, userToken, searchKey1, searchKey2, futureUserToken, effectiveDate, queueName);
    }

    public NotificationEventModelDao(final NotificationEventModelDao in, final String owner, final DateTime nextAvailable, final PersistentQueueEntryLifecycleState state) {
        super(in, owner, nextAvailable, state);
        this.futureUserToken = in.getFutureUserToken();
        this.effectiveDate = in.getEffectiveDate();
        this.queueName = in.getQueueName();
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
