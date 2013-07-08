package com.ning.billing.bus.api;


import java.util.UUID;

import com.ning.billing.queue.api.QueueEvent;

/**
 * Base interface for all bus/notiication  events
 */
public interface BusEvent extends QueueEvent {

    /**
     *
     * @return the search key1
     */
    public Long getSearchKey1();

    /**
     *
     * @return the search key2
     */
    public Long getSearchKey2();

    /**
     *
     * @return the user token
     */
    public UUID getUserToken();
}
