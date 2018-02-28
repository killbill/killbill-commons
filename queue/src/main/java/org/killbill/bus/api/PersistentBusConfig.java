/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.bus.api;

import org.killbill.queue.api.PersistentQueueConfig;
import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;
import org.skife.config.TimeSpan;


public abstract class PersistentBusConfig implements PersistentQueueConfig {

    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.inMemory")
    @Default("false")
    @Description("Whether the bus should be an in memory bus")
    public abstract boolean isInMemory();

    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.max.failure.retry")
    @Default("3")
    @Description("Number of retries for a given event when an exception occurs")
    public abstract int getMaxFailureRetries();

    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.claimed")
    @Default("10")
    @Description("Number of bus events to fetch from the database at once (only valid in 'polling mode')")
    public abstract int getMaxEntriesClaimed();

    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.queue.mode")
    @Default("STICKY_EVENTS")
    @Description("Number of bus events to dispatch from the inflightQ at once")
    public abstract PersistentQueueMode getPersistentQueueMode();

    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.claim.time")
    @Default("5m")
    @Description("Claim time")
    public abstract TimeSpan getClaimedTime();

    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.sleep")
    @Default("3000")
    @Description("Time in milliseconds to sleep between runs (only valid in STICKY_POLLING, POLLING)")
    public abstract long getPollingSleepTimeMs();

    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.off")
    @Default("false")
    @Description("Whether to turn off the persistent bus")
    public abstract boolean isProcessingOff();

    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.nbThreads")
    @Default("30")
    @Description("Max number of dispatch threads to use")
    public abstract int geMaxDispatchThreads();

    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.queue.capacity")
    @Default("30000")
    @Description("Size of the inflight queue (only valid in STICKY_EVENTS mode)")
    public abstract int getEventQueueCapacity();

    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.tableName")
    @Default("bus_events")
    @Description("Bus events table name")
    public abstract String getTableName();

    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.historyTableName")
    @Default("bus_events_history")
    @Description("Bus events history table name")
    public abstract String getHistoryTableName();

    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.reapThreshold")
    @Default("5m")
    @Description("Time span when the bus event must be re-dispatched")
    public abstract TimeSpan getReapThreshold();

    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.maxReDispatchCount")
    @Default("10")
    @Description("Max number of bus events to be re-dispatched at a time")
    public abstract int getMaxReDispatchCount();
}
