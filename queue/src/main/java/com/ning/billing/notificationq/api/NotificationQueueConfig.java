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

package com.ning.billing.notificationq.api;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;
import org.skife.config.TimeSpan;

import com.ning.billing.queue.api.PersistentQueueConfig;


public abstract class NotificationQueueConfig implements PersistentQueueConfig {

    @Config("killbill.billing.notificationq.${instanceName}.max.failure.retry")
    @Default("3")
    @Description("Number retry for a given event when an exception occurs")
    public abstract int getMaxFailureRetries();

    @Config("killbill.billing.notificationq.${instanceName}.claimed")
    @Default("7")
    @Description("Number of notifications to fetch at once")
    public abstract int getMaxEntriesClaimed();

    @Config("killbill.billing.notificationq.${instanceName}.claim.time")
    @Default("5m")
    @Description("Claim time")
    @Override
    public abstract TimeSpan getClaimedTime();

    @Override
    @Config("killbill.billing.notificationq.${instanceName}.sleep")
    @Default("3000")
    @Description("Time in milliseconds to sleep between runs")
    public abstract long getSleepTimeMs();

    @Override
    @Config("killbill.billing.notificationq.${instanceName}.notification.off")
    @Default("false")
    @Description("Whether to turn off the notification queue")
    public abstract boolean isProcessingOff();

    @Config("killbill.billing.notificationq.${instanceName}.notification.nbThreads")
    @Default("1")
    @Description("Number of threads to use")
    public abstract int getNbThreads();

    @Override
    @Config("killbill.billing.notificationq.${instanceName}.useInflightQ")
    @Default("false")
    @Description("Whether to use the inflight queue")
    public abstract boolean isUsingInflightQueue();

    @Config("killbill.billing.notificationq.${instanceName}.queue.capacity")
    @Default("0")
    @Description("Number of threads to use")
    public abstract int getQueueCapacity();

    @Config("killbill.billing.notificationq.${instanceName}.prefetch")
    @Default("7")
    @Description("Number of notifications to read from the database at once")
    public abstract int getPrefetchEntries();

    @Config("killbill.billing.notificationq.${instanceName}.tableName")
    @Default("notifications")
    @Description("Notifications table name")
    public abstract String getTableName();

    @Config("killbill.billing.notificationq.${instanceName}.historyTableName")
    @Default("notifications_history")
    @Description("Notifications history table name")
    public abstract String getHistoryTableName();
}
