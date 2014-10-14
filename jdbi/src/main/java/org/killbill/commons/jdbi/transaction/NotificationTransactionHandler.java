/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.commons.jdbi.transaction;

import org.killbill.commons.jdbi.notification.DatabaseTransactionEvent;
import org.killbill.commons.jdbi.notification.DatabaseTransactionEventType;
import org.killbill.commons.jdbi.notification.DatabaseTransactionNotificationApi;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.TransactionHandler;
import org.skife.jdbi.v2.tweak.transactions.DelegatingTransactionHandler;
import org.skife.jdbi.v2.tweak.transactions.LocalTransactionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A transaction handler that allows to notify observers about database transaction success/failure.
 */
public class NotificationTransactionHandler extends DelegatingTransactionHandler implements TransactionHandler {

    private static final Logger logger = LoggerFactory.getLogger(NotificationTransactionHandler.class);

    private final DatabaseTransactionNotificationApi transactionNotificationApi;

    public NotificationTransactionHandler(final DatabaseTransactionNotificationApi transactionNotificationApi) {
        super(new LocalTransactionHandler());
        this.transactionNotificationApi = transactionNotificationApi;
    }

    public void commit(Handle handle) {
        getDelegate().commit(handle);
        dispatchEvent(new DatabaseTransactionEvent(DatabaseTransactionEventType.COMMIT));
    }

    public void rollback(Handle handle) {
        getDelegate().rollback(handle);
        dispatchEvent(new DatabaseTransactionEvent(DatabaseTransactionEventType.ROLLBACK));
    }

    private void dispatchEvent(final DatabaseTransactionEvent event) {
        try {
            transactionNotificationApi.dispatchNotification(event);
        } catch (Exception e) {
            logger.warn("Failed to notify for event {}", event);
        }
    }
}
