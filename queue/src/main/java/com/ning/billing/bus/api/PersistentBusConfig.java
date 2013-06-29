package com.ning.billing.bus.api;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;

import com.ning.billing.queue.api.PersistentQueueConfig;

public abstract class PersistentBusConfig implements PersistentQueueConfig {

    @Config("killbill.billing.persistent.bus.prefetch")
    @Default("5")
    @Description("Number of bus events to fetch from the database at once")
    public abstract int getPrefetchAmount();

    @Override
    @Config("killbill.billing.persistent.bus.sleep")
    @Default("3000")
    @Description("Time in milliseconds to sleep between runs")
    public abstract long getSleepTimeMs();

    @Override
    @Config("killbill.billing.persistent.bus.off")
    @Default("false")
    @Description("Whether to turn off the persistent bus")
    public abstract boolean isProcessingOff();

    @Config("killbill.billing.persistent.bus.nbThreads")
    @Default("3")
    @Description("Number of threads to use")
    public abstract int getNbThreads();
}
