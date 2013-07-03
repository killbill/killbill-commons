package com.ning.billing.bus.api;

import java.util.UUID;

import org.joda.time.DateTime;

public class BusEvent<T extends BusEventBase> {

    private final Long recordId;
    private final UUID userToken;
    private final DateTime createdDate;
    private final Long searchKey1;
    private final Long searchKey2;
    private final T event;

    public BusEvent(final Long recordId, final UUID userToken, final DateTime createdDate, final Long searchKey1, final Long searchKey2, final T event) {
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
