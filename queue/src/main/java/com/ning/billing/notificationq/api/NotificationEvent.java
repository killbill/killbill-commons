package com.ning.billing.notificationq.api;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.bus.api.BusEvent;

/**
 * The NotificationEvent return to the user. It encapsulates the de-serialized version of the json event on disk.
 *
 * @param <T> The type of event serialized on disk
 */
public class NotificationEvent<T extends NotificationEventBase> extends BusEvent<T> {

    private final UUID futureUserToken;
    private final DateTime effectiveDate;
    private final String queueName;

    public NotificationEvent(final Long recordId, final UUID userToken, final DateTime createdDate, final Long searchKey1, final Long searchKey2, final T event) {
        super(recordId, userToken, createdDate, searchKey1, searchKey2, event);
        futureUserToken = null;
        effectiveDate = null;
        queueName = null;
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
