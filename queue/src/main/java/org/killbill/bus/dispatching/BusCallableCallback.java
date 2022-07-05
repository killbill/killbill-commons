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

package org.killbill.bus.dispatching;

import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.bus.DefaultPersistentBus;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.dao.BusEventModelDao;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.dispatching.CallableCallbackBase;

import org.killbill.common.eventbus.EventBusException;

public class BusCallableCallback extends CallableCallbackBase<BusEvent, BusEventModelDao> {

    private final DefaultPersistentBus parent;

    public BusCallableCallback(final DefaultPersistentBus parent) {
        super(parent.getDao(), parent.getClock(), parent.getConfig(), parent.getObjectMapper());
        this.parent = parent;
    }

    @Override
    public void dispatch(final BusEvent event, final BusEventModelDao modelDao) throws EventBusException {
        parent.dispatchBusEventWithMetrics(event);
    }

    @Override
    public BusEventModelDao buildEntry(final BusEventModelDao modelDao, final DateTime now, final PersistentQueueEntryLifecycleState newState, final long newErrorCount) {
        return new BusEventModelDao(modelDao, CreatorName.get(), now, newState, newErrorCount);
    }
}
