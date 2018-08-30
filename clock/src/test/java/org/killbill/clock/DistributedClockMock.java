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

package org.killbill.clock;

import org.joda.time.DateTime;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

public class DistributedClockMock extends ClockMock {

    private RedissonClient redissonClient;

    // Work around reset being called in parent default constructor
    public void setRedissonClient(final RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        reset();
    }

    @Override
    protected void setReferenceDateTimeUTC(final DateTime mockDateTimeUTC) {
        if (redissonClient == null) {
            super.setReferenceDateTimeUTC(mockDateTimeUTC);
            return;
        }
        final RAtomicLong referenceInstantUTC = getReferenceInstantUTC();
        referenceInstantUTC.set(mockDateTimeUTC.getMillis());
    }

    @Override
    protected DateTime getReferenceDateTimeUTC() {
        if (redissonClient == null) {
            return super.getReferenceDateTimeUTC();
        }
        final RAtomicLong referenceInstantUTC = getReferenceInstantUTC();
        return new DateTime(referenceInstantUTC.get());
    }

    private RAtomicLong getReferenceInstantUTC() {
        return redissonClient.getAtomicLong("ReferenceInstantUTC");
    }
}
