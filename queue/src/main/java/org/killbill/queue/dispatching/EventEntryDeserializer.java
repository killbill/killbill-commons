/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

import org.killbill.queue.api.QueueEvent;
import org.killbill.queue.dao.EventEntryModelDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectReader;

public final class EventEntryDeserializer {

    private static final Logger log = LoggerFactory.getLogger(EventEntryDeserializer.class);

    public static <E extends QueueEvent, M extends EventEntryModelDao> E deserialize(final M modelDao, final ObjectReader objectReader) {
        try {
            final Class<?> claz = Class.forName(modelDao.getClassName());
            return (E) objectReader.readValue(modelDao.getEventJson(), claz);
        } catch (final Exception e) {
            log.error("Failed to deserialize json object {} for class {}", modelDao.getEventJson(), modelDao.getClassName(), e);
            return null;
        }
    }
}
