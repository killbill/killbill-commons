/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.queue.api.QueueLifecycle;

/**
 * A service to create and delete NotificationQueue
 */
public interface NotificationQueueService extends QueueLifecycle {

    interface NotificationQueueHandler {

        /**
         * Called for each notification ready
         *
         * @param eventJson  the notification key associated to that notification entry
         * @param userToken  user token associated with that notification entry
         * @param searchKey1 the searchKey1 associated with that notification entry
         * @param searchKey2 the searchKey2 associated with that notification entry
         */
        void handleReadyNotification(NotificationEvent eventJson, DateTime eventDateTime, UUID userToken, Long searchKey1, Long searchKey2);
    }

    final class NotificationQueueAlreadyExists extends Exception {

        private static final long serialVersionUID = 1541281L;

        public NotificationQueueAlreadyExists(final String msg) {
            super(msg);
        }
    }

    final class NoSuchNotificationQueue extends Exception {

        private static final long serialVersionUID = 1561283L;

        public NoSuchNotificationQueue(final String msg) {
            super(msg);
        }
    }

    /**
     * Creates a new NotificationQueue for a given associated with the given service and queueName
     *
     * @param svcName   the name of the service using that queue
     * @param queueName a name for that queue (unique per service)
     * @param handler   the handler required for notifying the caller of state change
     * @return a new NotificationQueue
     * @throws NotificationQueueAlreadyExists is the queue associated with that service and name already exits
     */
    NotificationQueue createNotificationQueue(final String svcName, final String queueName, final NotificationQueueHandler handler)
            throws NotificationQueueAlreadyExists;

    /**
     * Retrieves an already created NotificationQueue by service and name if it exists
     *
     * @param svcName   the name of the service using that queue
     * @param queueName a name for that queue (unique per service)
     * @return a new NotificationQueue
     * @throws NoSuchNotificationQueue if queue does not exist
     */
    NotificationQueue getNotificationQueue(final String svcName, final String queueName)
            throws NoSuchNotificationQueue;

    /**
     * Delete notificationQueue
     *
     * @param svcName   the name of the service using that queue
     * @param queueName a name for that queue (unique per service)
     * @return a new NotificationQueue
     * @throws NoSuchNotificationQueue if queue does not exist
     */
    void deleteNotificationQueue(final String svcName, final String queueName)
            throws NoSuchNotificationQueue;

    /**
     * Retrieve all the notificationq registered
     *
     * @return
     */
    List<NotificationQueue> getNotificationQueues();
}
