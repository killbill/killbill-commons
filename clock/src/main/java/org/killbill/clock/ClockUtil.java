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

package org.killbill.clock;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

public class ClockUtil {

    /**
     * The method will convert the provided LocalDate into an instant (DateTime).
     * The conversion will use a reference time that should be interpreted from a UTC standpoint.
     *
     * The the provided LocalDate overlaps with the present, the current point in time is returned (to make sure we don't
     * end up with future instant).
     *
     * If not, we use both the provide LocalDate and LocalTime to return the DateTime in UTC
     *
     * @param inputDateInTargetTimeZone The input LocalDate as interpreted in the specified targetTimeZone
     * @param inputTimeInUTCTimeZone    The referenceTime in UTC
     * @param targetTimeZone            The target timeZone
     * @param clock                     The current clock
     * @return
     */
    public static DateTime computeDateTimeWithUTCReferenceTime(final LocalDate inputDateInTargetTimeZone, final LocalTime inputTimeInUTCTimeZone, final DateTimeZone targetTimeZone, final Clock clock) {

        final Interval interval = inputDateInTargetTimeZone.toInterval(targetTimeZone);
        // If the input date overlaps with the present, we return NOW.
        if (interval.contains(clock.getUTCNow())) {
            return clock.getUTCNow();
        }
        // If not, we convert the inputTimeInUTCTimeZone -> inputTimeInTargetTimeZone, compute the resulting DateTime in targetTimeZone, and convert into a UTC DateTime:
        final LocalTime inputTimeInTargetTimeZone = inputTimeInUTCTimeZone.plusMillis(targetTimeZone.getOffset(clock.getUTCNow()));
        final DateTime resultInTargetTimeZone = new DateTime(inputDateInTargetTimeZone.getYear(), inputDateInTargetTimeZone.getMonthOfYear(), inputDateInTargetTimeZone.getDayOfMonth(), inputTimeInTargetTimeZone.getHourOfDay(), inputTimeInTargetTimeZone.getMinuteOfHour(), inputTimeInTargetTimeZone.getSecondOfMinute(), targetTimeZone);
        return resultInTargetTimeZone.toDateTime(DateTimeZone.UTC);
    }
}
