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

package org.killbill.notificationq.dispatching;

import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.notificationq.NotificationQueueDispatcher;
import org.killbill.notificationq.NotificationQueueException;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.dao.NotificationEventModelDao;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.dispatching.CallableCallbackBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationCallableCallback extends CallableCallbackBase<NotificationEvent, NotificationEventModelDao> {

    private static final Logger log = LoggerFactory.getLogger(NotificationCallableCallback.class);
    private final NotificationQueueDispatcher parent;

    public NotificationCallableCallback(final NotificationQueueDispatcher parent) {
        super(parent.getDao(), parent.getClock(), parent.getConfig(), parent.getObjectMapper());
        this.parent = parent;
    }

    @Override
    public void dispatch(final NotificationEvent event, final NotificationEventModelDao modelDao) throws NotificationQueueException {
        final NotificationQueueService.NotificationQueueHandler handler = parent.getHandlerForActiveQueue(modelDao.getQueueName());
        if (handler == null) {
            // Will increment errorCount and eventually move to history table.
            throw new IllegalStateException(String.format("Cannot find handler for notification: queue = %s, record_id = %s",
                    modelDao.getQueueName(),
                    modelDao.getRecordId()));
        }
        parent.handleNotificationWithMetrics(handler, modelDao, event);
    }

    @Override
    public NotificationEventModelDao buildEntry(final NotificationEventModelDao modelDao, final DateTime now, final PersistentQueueEntryLifecycleState newState, final long newErrorCount) {
        return new NotificationEventModelDao(modelDao, CreatorName.get(), now, newState, newErrorCount);
    }

}
