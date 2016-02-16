/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.clock;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestClockUtil {

    @Test(groups = "fast")
    public void testWithInputInThePast() {
        final DateTimeZone inputTimeZone = DateTimeZone.forOffsetHours(1);
        final LocalDate inputDateInTargetTimeZone = new LocalDate(2014, 10, 23);
        final LocalTime inputTimeInUTC = new LocalTime(10, 23, 05);

        final DateTime result = ClockUtil.toUTCDateTime(inputDateInTargetTimeZone, inputTimeInUTC, inputTimeZone);
        Assert.assertEquals(result.compareTo(new DateTime(2014, 10, 23, 9, 23, 05, DateTimeZone.UTC)), 0);

        // ClockUtil should have returned a DateTime which when converted back into a LocalDate in the inputTimeZone matches the input
        Assert.assertEquals(result.toDateTime(inputTimeZone).toLocalDate().compareTo(inputDateInTargetTimeZone), 0);
    }

    @Test(groups = "fast")
    public void testWithInputInTheFuture() {
        final DateTimeZone inputTimeZone = DateTimeZone.forOffsetHours(-1);
        final LocalDate inputDateInTargetTimeZone = new LocalDate(2014, 10, 23);
        final LocalTime inputTimeInUTC = new LocalTime(10, 23, 05);

        final DateTime result = ClockUtil.toUTCDateTime(inputDateInTargetTimeZone, inputTimeInUTC, inputTimeZone);
        Assert.assertEquals(result.compareTo(new DateTime(2014, 10, 23, 11, 23, 05, DateTimeZone.UTC)), 0);

        // ClockUtil should have returned a DateTime which when converted back into a LocalDate in the inputTimeZone matches the input
        Assert.assertEquals(result.toDateTime(inputTimeZone).toLocalDate().compareTo(inputDateInTargetTimeZone), 0);
    }
}
