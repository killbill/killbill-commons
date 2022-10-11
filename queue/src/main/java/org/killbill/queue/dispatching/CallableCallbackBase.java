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

package org.killbill.queue.dispatching;

import org.joda.time.DateTime;
import org.killbill.clock.Clock;
import org.killbill.queue.DBBackedQueue;
import org.killbill.queue.api.PersistentQueueConfig;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.api.QueueEvent;
import org.killbill.queue.dao.EventEntryModelDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectReader;

public abstract class CallableCallbackBase<E extends QueueEvent, M extends EventEntryModelDao> implements CallableCallback<E, M> {

    private static final Logger log = LoggerFactory.getLogger(CallableCallbackBase.class);

    private final DBBackedQueue<M> dao;
    private final Clock clock;
    private final PersistentQueueConfig config;
    private final ObjectReader objectReader;

    public CallableCallbackBase(final DBBackedQueue<M> dao, final Clock clock, final PersistentQueueConfig config, final ObjectReader objectReader) {
        this.dao = dao;
        this.clock = clock;
        this.config = config;
        this.objectReader = objectReader;
    }

    @Override
    public E deserialize(final M modelDao) {
        return EventEntryDeserializer.deserialize(modelDao, objectReader);
    }

    @Override
    public void moveCompletedOrFailedEvents(final Iterable<M> entries) {
        dao.moveEntriesToHistory(entries);
    }

    @Override
    public void updateRetriedEvents(final M updatedEntry) {
        dao.updateOnError(updatedEntry);
    }

    @Override
    public abstract void dispatch(final E event, final M modelDao) throws Exception;

    public abstract M buildEntry(final M modelDao, final DateTime now, final PersistentQueueEntryLifecycleState newState, final long newErrorCount);

}
