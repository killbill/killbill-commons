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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.RateLimiter;

public class TestInfluxDB {

    private static final Logger logger = LoggerFactory.getLogger(TestInfluxDB.class);

    private static final String DEFAULT_INFLUX_ADDR = "http://127.0.0.1:8086";
    private static final String DEFAULT_INFLUX_DB = "killbill";
    private static final String DEFAULT_INFLUX_USERNAME = "killbill";
    private static final String DEFAULT_INFLUX_PWD = "killbill";

    private final RateLimiter rateLimiter;
    private final InfluxDB influxDb;

    private static final int MAX_BATCH = 3000;
    private static final double RATE_PER_SEC = (double) MAX_BATCH;

    private final AtomicLong counter;

    public TestInfluxDB() {
        influxDb = setupInfluxDB();
        rateLimiter = RateLimiter.create(RATE_PER_SEC);
        counter = new AtomicLong();
    }

    public void doIt() {

        while (true) {
            rateLimiter.acquire();
            writePoint();

            long value = counter.incrementAndGet();
            if (value % MAX_BATCH == 0) {
                logger.info("{} #={}", new DateTime(), value);
            }
        }

    }

    private void writePoint() {

        final Point point = Point.measurement("test_points")
                                 // We need to ensure that each point recorded in the same second has different 'features' otherwise influxdb treats those as duplicates
                                 // At the same time using a UUID would creates too many series per database and lead to error: InfluxDBException: partial write: max-series-per-database limit exceeded: (1000000) dropped=72
                                 .tag("uniq", String.valueOf(counter.get() % (MAX_BATCH + 13)))
                                 .addField("value", 1).build();
        influxDb.write(point);
    }

    private InfluxDB setupInfluxDB() {
        try {
            InfluxDB influxDB = InfluxDBFactory.connect(DEFAULT_INFLUX_ADDR, DEFAULT_INFLUX_USERNAME, DEFAULT_INFLUX_PWD);
            String dbName = DEFAULT_INFLUX_DB;
            influxDB.setDatabase(dbName);
            String rpName = "aRetentionPolicy2";
            influxDB.createRetentionPolicy(rpName, dbName, "1d", "30m", 1, true);
            influxDB.setRetentionPolicy(rpName);

            influxDB.enableBatch(MAX_BATCH, 20, TimeUnit.MILLISECONDS);
            return influxDB;

        } catch (org.influxdb.InfluxDBIOException e) {
            logger.warn("Failed to connect to influxDB, skip metrics");
        }
        return null;
    }

    public static void main(String[] args) {
        TestInfluxDB t = new TestInfluxDB();
        t.doIt();
    }

}
