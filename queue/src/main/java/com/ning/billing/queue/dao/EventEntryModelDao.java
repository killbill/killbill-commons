package com.ning.billing.queue.dao;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.queue.api.PersistentQueueEntryLifecycleState;

public interface EventEntryModelDao {

    Long getRecordId();

    String getClassName();

    String getEventJson();

    UUID getUserToken();

    String getProcessingOwner();

    String getCreatingOwner();

    DateTime getNextAvailableDate();

    PersistentQueueEntryLifecycleState getProcessingState();

    boolean isAvailableForProcessing(DateTime now);

    Long getSearchKey1();

    Long getSearchKey2();

}
