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

package org.killbill.clock;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import redis.embedded.RedisServer;

public class TestDistributedClockMock extends TestClockMockBase {

    private RedisServer redisServer;

    @BeforeMethod(groups = "slow")
    public void setUp() {
        if (System.getProperty("os.name").contains("Windows")) {
            redisServer = RedisServer.builder()
                                     .setting("maxheap 1gb")
                                     .port(56379)
                                     .build();
        } else {
            redisServer = new RedisServer(56379);
        }

        redisServer.start();
    }

    @AfterMethod(groups = "slow")
    public void tearDown() {
        redisServer.stop();
    }

    @Test(groups = "slow")
    public void testBasicClockOperations() throws Exception {
        final Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:56379");
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
