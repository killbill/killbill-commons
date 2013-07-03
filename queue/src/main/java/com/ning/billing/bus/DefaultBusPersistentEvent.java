package com.ning.billing.bus;

import java.util.UUID;

import com.ning.billing.bus.api.BusEvent;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class DefaultBusPersistentEvent implements BusEvent {


    private final UUID userToken;
    private final Long searchKey2;
    private final Long searchKey1;

    public DefaultBusPersistentEvent(final UUID userToken, final Long searchKey1, final Long searchKey2) {
        this.userToken = userToken;
        this.searchKey2 = searchKey2;
        this.searchKey1 = searchKey1;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }

    @JsonIgnore
    @Override
    public Long getSearchKey2() {
        return searchKey2;
    }

    @JsonIgnore
    @Override
    public Long getSearchKey1() {
        return searchKey1;
    }
}
