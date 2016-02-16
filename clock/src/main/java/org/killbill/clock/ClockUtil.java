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
import org.joda.time.IllegalInstantException;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

public class ClockUtil {

    /**
     * Create a DateTime object using the specified reference time and timezone
     *
     * @param localDate     LocalDate to convert
     * @param referenceTime Reference local time
     * @param dateTimeZone  Target timezone
     * @return DateTime representing the input localDate at the specified reference time, in UTC
     */
    public static DateTime toUTCDateTime(final LocalDate localDate, final LocalTime referenceTime, final DateTimeZone dateTimeZone) {
        DateTime targetDateTime;
        try {
            targetDateTime = new DateTime(localDate.getYear(),
                                          localDate.getMonthOfYear(),
                                          localDate.getDayOfMonth(),
                                          referenceTime.getHourOfDay(),
                                          referenceTime.getMinuteOfHour(),
                                          referenceTime.getSecondOfMinute(),
                                          dateTimeZone);
        } catch (final IllegalInstantException e) {
            // DST gap (shouldn't happen when using fixed offset timezones)
            targetDateTime = localDate.toDateTimeAtStartOfDay(dateTimeZone);
        }

        return toUTCDateTime(targetDateTime);
    }

    /**
     * Create a LocalDate object using the specified timezone
     *
     * @param dateTime     DateTime to convert
     * @param dateTimeZone Target timezone
     * @return LocalDate representing the input dateTime in the specified timezone
     */
    public static LocalDate toLocalDate(final DateTime dateTime, final DateTimeZone dateTimeZone) {
        return new LocalDate(dateTime, dateTimeZone);
    }

    /**
     * Create a DateTime object forcing the timezone to be UTC
     *
     * @param dateTime DateTime to convert
     * @return DateTime representing the input dateTime in UTC
     */
    public static DateTime toUTCDateTime(final DateTime dateTime) {
        return toDateTime(dateTime, DateTimeZone.UTC);
    }

    /**
     * Create a DateTime object using the specified timezone
     *
     * @param dateTime        DateTime to convert
     * @param accountTimeZone Target timezone
     * @return DateTime representing the input dateTime in the specified timezone
     */
    public static DateTime toDateTime(final DateTime dateTime, final DateTimeZone accountTimeZone) {
        return dateTime.toDateTime(accountTimeZone);
    }
}
