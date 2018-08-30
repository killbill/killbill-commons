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

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testng.annotations.Test;

public class TestDistributedClockMock extends TestClockMockBase {

    @Test(groups = "redis")
    public void testBasicClockOperations() throws Exception {
        final Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setConnectionMinimumIdleSize(10);
        final RedissonClient redissonClient = Redisson.create(config);
        try {
            final DistributedClockMock clock = new DistributedClockMock();
            clock.setRedissonClient(redissonClient);
            testBasicClockOperations(clock);
        } finally {
            redissonClient.shutdown();
        }
    }
}
