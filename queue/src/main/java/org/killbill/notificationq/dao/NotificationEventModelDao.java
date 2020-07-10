/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.notificationq.dao;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;

public class NotificationEventModelDao extends BusEventModelDao {

    private UUID futureUserToken;
    private DateTime effectiveDate;
    private String queueName;

    public NotificationEventModelDao() { /* Default ctor for jdbi mapper */ }

    public NotificationEventModelDao(final long id, final String createdOwner, final String owner, final DateTime createdDate, final DateTime nextAvailable, final PersistentQueueEntryLifecycleState processingState,
                                     final String eventJsonClass, final String eventJson, final Long errorCount, final UUID userToken, final Long searchKey1, final Long searchKey2, final UUID futureUserToken, final DateTime effectiveDate, final String queueName) {
        super(id, createdOwner, owner, createdDate, nextAvailable, processingState, eventJsonClass, eventJson, errorCount, userToken, searchKey1, searchKey2);
        this.futureUserToken = futureUserToken;
        this.effectiveDate = effectiveDate;
        this.queueName = queueName;
    }

    public NotificationEventModelDao(final String createdOwner, final DateTime createdDate, final String eventJsonClass,
                                     final String eventJson, final UUID userToken, final Long searchKey1, final Long searchKey2, final UUID futureUserToken, final DateTime effectiveDate, final String queueName) {
        this(-1L, createdOwner, null, createdDate, null, PersistentQueueEntryLifecycleState.AVAILABLE,
             eventJsonClass, eventJson, 0L, userToken, searchKey1, searchKey2, futureUserToken, effectiveDate, queueName);
    }

    public NotificationEventModelDao(final NotificationEventModelDao in, final String owner, final DateTime nextAvailable, final PersistentQueueEntryLifecycleState state) {
        this(in.getRecordId(), in.getCreatingOwner(), owner, in.getCreatedDate(), nextAvailable, state, in.getClassName(), in.getEventJson(), in.getErrorCount(), in.getUserToken(), in.getSearchKey1(), in.getSearchKey2(), in.getFutureUserToken(), in.getEffectiveDate(), in.getQueueName());
    }

    public NotificationEventModelDao(final NotificationEventModelDao in, final String owner, final DateTime nextAvailable, final PersistentQueueEntryLifecycleState state, final Long errorCount) {
        this(in.getRecordId(), in.getCreatingOwner(), owner, in.getCreatedDate(), nextAvailable, state, in.getClassName(), in.getEventJson(), errorCount, in.getUserToken(), in.getSearchKey1(), in.getSearchKey2(), in.getFutureUserToken(), in.getEffectiveDate(), in.getQueueName());
    }

    public UUID getFutureUserToken() {
        return futureUserToken;
    }

    public void setFutureUserToken(final UUID futureUserToken) {
        this.futureUserToken = futureUserToken;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(final DateTime effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(final String queueName) {
        this.queueName = queueName;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("NotificationEventModelDao{");
        sb.append("recordId=").append(getRecordId());
        sb.append(", className='").append(getClassName()).append('\'');
        sb.append(", eventJson='").append(getEventJson()).append('\'');
        sb.append(", userToken=").append(getUserToken());
        sb.append(", createdDate=").append(getCreatedDate());
        sb.append(", creatingOwner='").append(getCreatingOwner()).append('\'');
        sb.append(", processingOwner='").append(getProcessingOwner()).append('\'');
        sb.append(", processingAvailableDate=").append(getProcessingAvailableDate());
        sb.append(", errorCount=").append(getErrorCount());
        sb.append(", processingState=").append(getProcessingState());
        sb.append(", searchKey1=").append(getSearchKey1());
        sb.append(", searchKey2=").append(getSearchKey2());
        sb.append(", futureUserToken=").append(futureUserToken);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", queueName='").append(queueName).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
