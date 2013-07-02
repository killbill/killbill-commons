package com.ning.billing.queue.api;

import org.skife.config.TimeSpan;

public interface PersistentQueueConfig {

    public int getMaxEntriesClaimed();

    public TimeSpan getClaimedTime();

    public long getSleepTimeMs();

    public boolean isProcessingOff();

    public int getNbThreads();
}
