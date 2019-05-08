/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.queue;

import org.killbill.bus.BusReaper;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestReaperConfig {

    @Test(groups = "fast")
    public void testDefaultReaperSchedule() {

        final DefaultReaper reaper = new BusReaper(null, null, null);

        final long defaultScheduleMilliSec = reaper.getSchedulePeriod();
        Assert.assertEquals(defaultScheduleMilliSec, DefaultReaper.THREE_MINUTES_IN_MSEC);
    }

    @Test(groups = "fast")
    public void testDefaultOverloadedReaperSchedule() {

        final DefaultReaper reaper = new BusReaper(null, null, null);

        try {
            System.setProperty(DefaultReaper.REAPER_SCHEDULE_PROP, "7m"); // 420000 mSec
            final long defaultScheduleMilliSec = reaper.getSchedulePeriod();
            Assert.assertEquals(defaultScheduleMilliSec, 420000);
        } finally {
            System.clearProperty(DefaultReaper.REAPER_SCHEDULE_PROP);
        }
    }

}
