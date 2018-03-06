/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.bus.dao;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.dao.EventEntryModelDao;

public class BusEventModelDao implements EventEntryModelDao {

    private Long recordId;
    private String className;
    private String eventJson;
    private UUID userToken;
    private DateTime createdDate;
    private String creatingOwner;
    private String processingOwner;
    private DateTime processingAvailableDate;
    private Long errorCount;
    private PersistentQueueEntryLifecycleState processingState;
    private Long searchKey1;
    private Long searchKey2;

    public BusEventModelDao() { /* DAO mapper */ }

    public BusEventModelDao(final Long recordId, final String createdOwner, final String owner, final DateTime createdDate, final DateTime nextAvailable,
                            final PersistentQueueEntryLifecycleState processingState, final String busEventClass, final String busEventJson, final Long errorCount,
                            final UUID userToken, final Long searchKey1, final Long searchKey2) {
        this.recordId = recordId;
        this.creatingOwner = createdOwner;
        this.processingOwner = owner;
        this.createdDate = createdDate;
        this.processingAvailableDate = nextAvailable;
        this.processingState = processingState;
        this.className = busEventClass;
        this.errorCount = errorCount;
        this.eventJson = busEventJson;
        this.userToken = userToken;
        this.searchKey1 = searchKey1;
        this.searchKey2 = searchKey2;
    }

    public BusEventModelDao(final String createdOwner, final DateTime createdDate, final String busEventClass, final String busEventJson,
                            final UUID userToken, final Long searchKey1, final Long searchKey2) {
        this(-1L, createdOwner, null, createdDate, null, PersistentQueueEntryLifecycleState.AVAILABLE, busEventClass, busEventJson, 0L, userToken, searchKey1, searchKey2);
    }

    public BusEventModelDao(final BusEventModelDao in, final String owner, final DateTime nextAvailable, final PersistentQueueEntryLifecycleState state) {
        this(in.getRecordId(), in.getCreatingOwner(), owner, in.getCreatedDate(), nextAvailable, state, in.getClassName(), in.getEventJson(), in.getErrorCount(), in.getUserToken(), in.getSearchKey1(), in.getSearchKey2());
    }

    public BusEventModelDao(final BusEventModelDao in, final String owner, final DateTime nextAvailable, final PersistentQueueEntryLifecycleState state, final Long errorCount) {
        this(in.getRecordId(), in.getCreatingOwner(), owner, in.getCreatedDate(), nextAvailable, state, in.getClassName(), in.getEventJson(), errorCount, in.getUserToken(), in.getSearchKey1(), in.getSearchKey2());
    }

    @Override
    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(final Long recordId) {
        this.recordId = recordId;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public void setClassName(final String className) {
        this.className = className;
    }

    @Override
    public String getEventJson() {
        return eventJson;
    }

    @Override
    public void setEventJson(final String eventJson) {
        this.eventJson = eventJson;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }

    @Override
    public void setUserToken(final UUID userToken) {
        this.userToken = userToken;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public void setCreatedDate(final DateTime createdDate) {
        this.createdDate = createdDate;
    }

    @Override
    public String getCreatingOwner() {
        return creatingOwner;
    }

    @Override
    public void setCreatingOwner(final String creatingOwner) {
        this.creatingOwner = creatingOwner;
    }

    @Override
    public String getProcessingOwner() {
        return processingOwner;
    }

    @Override
    public void setProcessingOwner(final String processingOwner) {
        this.processingOwner = processingOwner;
    }

    @Override
    public DateTime getNextAvailableDate() {
        return processingAvailableDate;
    }

    // Required for serialization
    public DateTime getProcessingAvailableDate() {
        return processingAvailableDate;
    }

    public void setProcessingAvailableDate(final DateTime processingAvailableDate) {
        this.processingAvailableDate = processingAvailableDate;
    }

    @Override
    public void setErrorCount(final Long errorCount) {
        this.errorCount = errorCount;
    }

    @Override
    public PersistentQueueEntryLifecycleState getProcessingState() {
        return processingState;
    }

    @Override
    public void setProcessingState(final PersistentQueueEntryLifecycleState processingState) {
        this.processingState = processingState;
    }

    @Override
    public void setSearchKey1(final Long searchKey1) {
        this.searchKey1 = searchKey1;
    }

    @Override
    public void setSearchKey2(final Long searchKey2) {
        this.searchKey2 = searchKey2;
    }

    @Override
    public boolean isAvailableForProcessing(final DateTime now) {
        switch (processingState) {
            case AVAILABLE:
                break;
            case IN_PROCESSING:
                // Somebody already got the event, not available yet
                if (processingAvailableDate.isAfter(now)) {
                    return false;
                }
                break;
            case PROCESSED:
                return false;
            default:
                throw new RuntimeException(String.format("Unknown IEvent processing state %s", processingState));
        }
        return true;
    }

    @Override
    public Long getErrorCount() {
        return errorCount;
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
    public String toString() {
        final StringBuilder sb = new StringBuilder("BusEventModelDao{");
        sb.append("recordId=").append(recordId);
        sb.append(", className='").append(className).append('\'');
        sb.append(", eventJson='").append(eventJson).append('\'');
        sb.append(", userToken=").append(userToken);
        sb.append(", createdDate=").append(createdDate);
        sb.append(", creatingOwner='").append(creatingOwner).append('\'');
        sb.append(", processingOwner='").append(processingOwner).append('\'');
        sb.append(", processingAvailableDate=").append(processingAvailableDate);
        sb.append(", errorCount=").append(errorCount);
        sb.append(", processingState=").append(processingState);
        sb.append(", searchKey1=").append(searchKey1);
        sb.append(", searchKey2=").append(searchKey2);
        sb.append('}');
        return sb.toString();
    }
}
