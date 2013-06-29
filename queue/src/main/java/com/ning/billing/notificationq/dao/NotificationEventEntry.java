package com.ning.billing.notificationq.dao;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.bus.dao.BusEventEntry;

public class NotificationEventEntry extends BusEventEntry {

    private final UUID futureUserToken;
    private final DateTime effectiveDate;
    private final String queueName;

    public NotificationEventEntry(final long id, final String createdOwner, final String owner, final DateTime nextAvailable, final PersistentQueueEntryLifecycleState processingState,
                                  final String eventJsonClass, final String eventJson, final UUID userToken, final Long searchKey1, final Long searchKey2, final UUID futureUserToken, final DateTime effectiveDate, final String queueName) {
        super(id, createdOwner, owner, nextAvailable, processingState, eventJsonClass, eventJson, userToken, searchKey1, searchKey2);
        this.futureUserToken = futureUserToken;
        this.effectiveDate = effectiveDate;
        this.queueName = queueName;
    }

    public NotificationEventEntry(final String createdOwner, final String eventJsonClass,
                               final String eventJson, final UUID userToken,  final Long searchKey1, final Long searchKey2, final UUID futureUserToken, final DateTime effectiveDate, final String queueName) {
        this(-1L, createdOwner, null, null, PersistentQueueEntryLifecycleState.AVAILABLE,
             eventJsonClass, eventJson, userToken, searchKey1, searchKey2, futureUserToken, effectiveDate, queueName);
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
