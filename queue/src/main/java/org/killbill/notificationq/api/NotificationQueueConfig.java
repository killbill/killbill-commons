/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.killbill.notificationq.api;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;
import org.skife.config.TimeSpan;

import org.killbill.queue.api.PersistentQueueConfig;

public abstract class NotificationQueueConfig implements PersistentQueueConfig {

    @Override
    @Config("org.killbill.notificationq.${instanceName}.inMemory")
    @Default("false")
    @Description("Set to false, not available for NotificationQueue")
    public abstract boolean isInMemory();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.max.failure.retry")
    @Default("3")
    @Description("Number retry for a given event when an exception occurs")
    public abstract int getMaxFailureRetries();


    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.inflight.min")
    @Default("-1")
    @Description("Min number of bus events to fetch from the database at once (only valid in 'STICKY_EVENTS')")
    public abstract int getMinInFlightEntries();

    @Override
    @Config("org.killbill.persistent.bus.${instanceName}.inflight.max")
    @Default("-1")
    @Description("Max number of bus events to fetch from the database at once (only valid in 'STICKY_EVENTS')")
    public abstract int getMaxInFlightEntries();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.claimed")
    @Default("10")
    @Description("Number of notifications to fetch at once")
    public abstract int getMaxEntriesClaimed();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.queue.mode")
    @Default("POLLING")
    @Description("How entries are put in the queue")
    public abstract PersistentQueueMode getPersistentQueueMode();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.claim.time")
    @Default("5m")
    @Description("Claim time")
    public abstract TimeSpan getClaimedTime();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.sleep")
    @Default("3000")
    @Description("Time in milliseconds to sleep between runs")
    public abstract long getPollingSleepTimeMs();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.notification.off")
    @Default("false")
    @Description("Whether to turn off the notification queue")
    public abstract boolean isProcessingOff();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.notification.nbThreads")
    @Default("10")
    @Description("Number of threads to use")
    public abstract int geMaxDispatchThreads();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.lifecycle.dispatch.nbThreads")
    @Default("1")
    @Description("Max number of lifecycle dispatch threads to use")
    public abstract int geNbLifecycleDispatchThreads();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.lifecycle.complete.nbThreads")
    @Default("2")
    @Description("Max number of lifecycle complete threads to use")
    public abstract int geNbLifecycleCompleteThreads();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.queue.capacity")
    @Default("100")
    @Description("Capacity for the worker queue")
    public abstract int getEventQueueCapacity();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.tableName")
    @Default("notifications")
    @Description("Notifications table name")
    public abstract String getTableName();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.historyTableName")
    @Default("notifications_history")
    @Description("Notifications history table name")
    public abstract String getHistoryTableName();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.reapThreshold")
    @Default("10m")
    @Description("Time span when a notification must be re-dispatched")
    public abstract TimeSpan getReapThreshold();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.maxReDispatchCount")
    @Default("10")
    @Description("Max number of notification to be re-dispatched at a time")
    public abstract int getMaxReDispatchCount();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.reapSchedule")
    @Default("3m")
    @Description("Reaper schedule period")
    public abstract TimeSpan getReapSchedule();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.shutdownTimeout")
    @Default("15s")
    @Description("Shutdown sequence timeout")
    public abstract TimeSpan getShutdownTimeout();
}
