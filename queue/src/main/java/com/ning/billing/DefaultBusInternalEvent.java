package com.ning.billing;

import java.util.UUID;

import com.ning.billing.bus.BusPersistentEvent;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class DefaultBusInternalEvent implements BusPersistentEvent {


    private final UUID userToken;
    private final Long tenantRecordId;
    private final Long accountRecordId;

    public DefaultBusInternalEvent(final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        this.userToken = userToken;
        this.tenantRecordId = tenantRecordId;
        this.accountRecordId = accountRecordId;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }

    @JsonIgnore
    @Override
    public Long getTenantRecordId() {
        return tenantRecordId;
    }

    @JsonIgnore
    @Override
    public Long getAccountRecordId() {
        return accountRecordId;
    }
}
