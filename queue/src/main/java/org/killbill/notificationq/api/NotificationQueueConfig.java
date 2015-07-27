/*
 * Copyright 2010-2012 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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
    @Description("Whether the implementation should be an in memory (set to false, not available for NotificationQueue)")
    public abstract boolean isInMemory();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.sticky")
    @Default("false")
    @Description("Whether a node should only pick entries it inserted")
    public abstract boolean isSticky();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.max.failure.retry")
    @Default("3")
    @Description("Number retry for a given event when an exception occurs")
    public abstract int getMaxFailureRetries();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.claimed")
    @Default("10")
    @Description("Number of notifications to fetch at once")
    public abstract int getMaxEntriesClaimed();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.inflight.claimed (set to -1, not available for NotificationQueue)")
    @Default("-1")
    @Description("Number of notifications to fetch at once")
    public abstract int getMaxInflightQEntriesClaimed();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.claim.time")
    @Default("5m")
    @Description("Claim time")
    public abstract TimeSpan getClaimedTime();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.sleep")
    @Default("3000")
    @Description("Time in milliseconds to sleep between runs")
    public abstract long getSleepTimeMs();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.notification.off")
    @Default("false")
    @Description("Whether to turn off the notification queue")
    public abstract boolean isProcessingOff();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.notification.nbThreads")
    @Default("10")
    @Description("Number of threads to use")
    public abstract int getNbThreads();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.useInflightQ (set to false, not available for NotificationQueue)")
    @Default("false")
    @Description("Whether to use the inflight queue")
    public abstract boolean isUsingInflightQueue();

    @Override
    @Config("org.killbill.notificationq.${instanceName}.queue.capacity")
    @Default("30000")
    @Description("Capacity for the worker queue")
    public abstract int getQueueCapacity();

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
}
