package com.ning.billing.queue.api;

import java.util.UUID;

import org.joda.time.DateTime;

public interface EventEntry {

    Long getRecordId();

    String getEventClass();

    String getEventJson();

    UUID getUserToken();

    String getOwner();

    String getCreatedOwner();

    DateTime getNextAvailableDate();

    PersistentQueueEntryLifecycleState getProcessingState();

    boolean isAvailableForProcessing(DateTime now);

    Long getSearchKey1();

    Long getSearchKey2();

    enum PersistentQueueEntryLifecycleState {
        AVAILABLE,
        IN_PROCESSING,
        PROCESSED,
        REMOVED
    }
}
