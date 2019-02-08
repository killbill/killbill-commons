/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.bus.integration;

import java.util.concurrent.atomic.AtomicLong;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

import static org.killbill.bus.integration.TestQueueRemoteServer.DEFAULT_INFLUX_BATCH;

public class TestBusHandler {

    private final Logger logger = LoggerFactory.getLogger(TestBusHandler.class);


    private final InfluxDB influxDB;

    private final AtomicLong counter;

    private final long sleepTimeMsec;

    public TestBusHandler(final InfluxDB influxDB, final long handlerLatency) {
        this.influxDB = influxDB;
        this.counter = new AtomicLong();

        this.sleepTimeMsec = handlerLatency;
        logger.info("BusHandler configured to sleep {} mSec", sleepTimeMsec);
    }

    @AllowConcurrentEvents
    @Subscribe
    public void dispatchEvent(final TestBusEvent event) {
        long dispatched = counter.incrementAndGet();

        sleepIfRequired();

        if (influxDB != null) {
            influxDB.write(Point.measurement("dispatched_events")
                                .tag("source", event.getSource())
                                .tag("uniq", String.valueOf(dispatched % (DEFAULT_INFLUX_BATCH + 13)))
                                .addField("searchKey1", event.getSearchKey1())
                                .addField("searchKey2", event.getSearchKey2())
                                .build());
        }

    }


    private void sleepIfRequired() {
        if (sleepTimeMsec > 0) {
            try {
                Thread.sleep(sleepTimeMsec);
            } catch (final Exception e) {

            }
        }
    }
}
