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

package org.killbill.queue.api;

import org.skife.config.TimeSpan;

public interface PersistentQueueConfig {

    // We support 3 different modes to the queue
    enum PersistentQueueMode {
        // Entries written from a given node (server) will also be dispatched to that same node; the code will poll for new entries
        STICKY_POLLING,
        // Entries written from a given node (server) will also be dispatched to that same node; the code will react to database commit/abort events to fetch new entries
        SITCKY_EVENTS,
        // Entries written from a given node (server) will may be dispatched to any nodes by polling for all available entries
        POLLING
    }

    boolean isInMemory();

    int getMaxFailureRetries();

    PersistentQueueMode getPersistentQueueMode();

    int getMaxEntriesClaimed();

    TimeSpan getClaimedTime();

    long getPollingSleepTimeMs();

    boolean isProcessingOff();

    int getEventQueueCapacity();

    int geMaxDispatchThreads();

    String getTableName();

    String getHistoryTableName();
}
