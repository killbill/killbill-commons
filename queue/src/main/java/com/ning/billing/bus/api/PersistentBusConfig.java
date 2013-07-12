/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.bus.api;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;
import org.skife.config.TimeSpan;

import com.ning.billing.queue.api.PersistentQueueConfig;

public abstract class PersistentBusConfig implements PersistentQueueConfig {

    @Config("killbill.billing.persistent.bus.claimed")
    @Default("5")
    @Description("Number of bus events to fetch from the database at once")
    public abstract int getMaxEntriesClaimed();

    @Config("killbill.billing.persistent.bus.claim.time")
    @Default("5m")
    @Description("Claim time")
    @Override
    public abstract TimeSpan getClaimedTime();

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

    @Override
    @Config("killbill.billing.persistent.bus.useInflightQ")
    @Default("false")
    @Description("Whether to use the inflight queue")
    public abstract boolean isUsingInflightQueue();

    @Config("killbill.billing.persistent.bus.queue.capacity")
    @Default("3000")
    @Description("Number of threads to use")
    public abstract int getQueueCapacity();

    @Config("killbill.billing.persistent.bus.queue.prefetch")
    @Default("7")
    @Description("Number of notifications to read from the database at once")
    public abstract int getPrefetchEntries();

}
