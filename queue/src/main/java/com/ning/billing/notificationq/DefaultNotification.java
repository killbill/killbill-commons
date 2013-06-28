/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.notificationq;

import java.util.UUID;

import org.joda.time.DateTime;


public class DefaultNotification implements Notification {

    private final Long recordId;
    private final String owner;
    private final String createdOwner;
    private final String queueName;
    private final DateTime nextAvailableDate;
    private final PersistentQueueEntryLifecycleState lifecycleState;
    private final String notificationKeyClass;
    private final String notificationKey;
    private final UUID userToken;
    private final UUID futureUserToken;
    private final DateTime effectiveDate;
    private final Long searchKey1;
    private final Long searchKey2;

    public DefaultNotification(final Long recordId, final String createdOwner, final String owner, final String queueName,
                               final DateTime nextAvailableDate, final PersistentQueueEntryLifecycleState lifecycleState,
                               final String notificationKeyClass, final String notificationKey, final UUID userToken, final UUID futureUserToken,
                               final DateTime effectiveDate, final Long searchKey1, final Long searchKey2) {
        this.recordId = recordId;
        this.owner = owner;
        this.createdOwner = createdOwner;
        this.queueName = queueName;
        this.nextAvailableDate = nextAvailableDate;
        this.lifecycleState = lifecycleState;
        this.notificationKeyClass = notificationKeyClass;
        this.notificationKey = notificationKey;
        this.userToken = userToken;
        this.futureUserToken = futureUserToken;
        this.effectiveDate = effectiveDate;
        this.searchKey1 = searchKey1;
        this.searchKey2 = searchKey2;
    }

    public DefaultNotification(final String queueName, final String createdOwner, final String notificationKeyClass,
                               final String notificationKey, final UUID userToken, final UUID futureUserToken, final DateTime effectiveDate,
                               final Long searchKey1, final Long searchKey2) {
        this(-1L, createdOwner, null, queueName, null, PersistentQueueEntryLifecycleState.AVAILABLE,
             notificationKeyClass, notificationKey, userToken, futureUserToken, effectiveDate, searchKey1, searchKey2);
    }


    @Override
    public Long getRecordId() {
        return recordId;
    }

    @Override
    public String getOwner() {
        return owner;
    }

    @Override
    public DateTime getNextAvailableDate() {
        return nextAvailableDate;
    }

    @Override
    public PersistentQueueEntryLifecycleState getProcessingState() {
        return lifecycleState;
    }

    @Override
    public boolean isAvailableForProcessing(final DateTime now) {
        switch (lifecycleState) {
            case AVAILABLE:
                break;
            case IN_PROCESSING:
                // Somebody already got the event, not available yet
                if (nextAvailableDate.isAfter(now)) {
                    return false;
                }
                break;
            case PROCESSED:
                return false;
            default:
                throw new RuntimeException(String.format("Unkwnon IEvent processing state %s", lifecycleState));
        }
        return effectiveDate.isBefore(now);
    }

    @Override
    public String getNotificationKeyClass() {
        return notificationKeyClass;
    }

    @Override
    public String getNotificationKey() {
        return notificationKey;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public String getQueueName() {
        return queueName;
    }

    @Override
    public String getCreatedOwner() {
        return createdOwner;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }

    @Override
    public UUID getFutureUserToken() {
        return futureUserToken;
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultNotification that = (DefaultNotification) o;

        if (recordId != that.recordId) {
            return false;
        }
        if (searchKey1 != null ? !searchKey1.equals(that.searchKey1) : that.searchKey1 != null) {
            return false;
        }
        if (createdOwner != null ? !createdOwner.equals(that.createdOwner) : that.createdOwner != null) {
            return false;
        }
        if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
            return false;
        }
        if (futureUserToken != null ? !futureUserToken.equals(that.futureUserToken) : that.futureUserToken != null) {
            return false;
        }
        if (lifecycleState != that.lifecycleState) {
            return false;
        }
        if (nextAvailableDate != null ? !nextAvailableDate.equals(that.nextAvailableDate) : that.nextAvailableDate != null) {
            return false;
        }
        if (notificationKey != null ? !notificationKey.equals(that.notificationKey) : that.notificationKey != null) {
            return false;
        }
        if (notificationKeyClass != null ? !notificationKeyClass.equals(that.notificationKeyClass) : that.notificationKeyClass != null) {
            return false;
        }
        if (owner != null ? !owner.equals(that.owner) : that.owner != null) {
            return false;
        }
        if (queueName != null ? !queueName.equals(that.queueName) : that.queueName != null) {
            return false;
        }
        if (searchKey2 != null ? !searchKey2.equals(that.searchKey2) : that.searchKey2 != null) {
            return false;
        }
        if (userToken != null ? !userToken.equals(that.userToken) : that.userToken != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) (recordId ^ (recordId >>> 32));
        result = 31 * result + (owner != null ? owner.hashCode() : 0);
        result = 31 * result + (createdOwner != null ? createdOwner.hashCode() : 0);
        result = 31 * result + (queueName != null ? queueName.hashCode() : 0);
        result = 31 * result + (nextAvailableDate != null ? nextAvailableDate.hashCode() : 0);
        result = 31 * result + (lifecycleState != null ? lifecycleState.hashCode() : 0);
        result = 31 * result + (notificationKeyClass != null ? notificationKeyClass.hashCode() : 0);
        result = 31 * result + (notificationKey != null ? notificationKey.hashCode() : 0);
        result = 31 * result + (userToken != null ? userToken.hashCode() : 0);
        result = 31 * result + (futureUserToken != null ? futureUserToken.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (searchKey1 != null ? searchKey1.hashCode() : 0);
        result = 31 * result + (searchKey2 != null ? searchKey2.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultNotification");
        sb.append("{recordId=").append(recordId);
        sb.append(", owner='").append(owner).append('\'');
        sb.append(", createdOwner='").append(createdOwner).append('\'');
        sb.append(", queueName='").append(queueName).append('\'');
        sb.append(", nextAvailableDate=").append(nextAvailableDate);
        sb.append(", lifecycleState=").append(lifecycleState);
        sb.append(", notificationKeyClass='").append(notificationKeyClass).append('\'');
        sb.append(", notificationKey='").append(notificationKey).append('\'');
        sb.append(", userToken=").append(userToken);
        sb.append(", futureUserToken=").append(futureUserToken);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", searchKey1=").append(searchKey1);
        sb.append(", searchKey2=").append(searchKey2);
        sb.append('}');
        return sb.toString();
    }
}
