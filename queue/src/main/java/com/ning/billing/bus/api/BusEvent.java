package com.ning.billing.bus.api;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;

import com.ning.billing.queue.api.PersistentQueueConfig;

public interface BusEvent {
    public Long getRecordId();
    public BusEventJson getBusEventJson();

}
