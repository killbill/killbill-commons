package com.ning.billing.queue.api;

public interface PersistentQueueConfig {

    public int getPrefetchAmount();

    public long getSleepTimeMs();

    public boolean isProcessingOff();

    public int getNbThreads();
}
