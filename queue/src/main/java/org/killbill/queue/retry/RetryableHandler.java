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

package org.killbill.queue.retry;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.util.queue.QueueRetryException;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;

public class RetryableHandler implements NotificationQueueHandler {

    protected final Clock clock;

    private final RetryableService retryableService;

    private final NotificationQueueHandler handlerDelegate;

    public RetryableHandler(final Clock clock,
                            final RetryableService retryableService,
                            final NotificationQueueHandler handlerDelegate) {
        this.clock = clock;
        this.retryableService = retryableService;
        this.handlerDelegate = handlerDelegate;
    }

    @Override
    public void handleReadyNotification(final NotificationEvent notificationEvent, final DateTime eventDateTime, final UUID userToken, final Long searchKey1, final Long searchKey2) {
        try {
            handlerDelegate.handleReadyNotification(notificationEvent, eventDateTime, userToken, searchKey1, searchKey2);
        } catch (final QueueRetryException e) {
            // Let the retry queue handle the exception
            retryableService.scheduleRetry(e, notificationEvent, eventDateTime, userToken, searchKey1, searchKey2, 1);
        }
    }
}
