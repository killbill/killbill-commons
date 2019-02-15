/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package org.killbill.bus;

import java.util.concurrent.TimeUnit;

import org.killbill.bus.api.PersistentBusConfig;
import org.skife.config.TimeSpan;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.bus.api.PersistentBus;


public class TestInMemoryEventBus {

    private TestEventBusBase testEventBusBase;
    private PersistentBus busService;

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        busService = new InMemoryPersistentBus(new PersistentBusConfig() {
            @Override
            public boolean isInMemory() {
                return false;
            }
            @Override
            public int getMaxFailureRetries() {
                return 0;
            }

            @Override
            public int getMinInFlightEntries() {
                return 1;
            }
            @Override
            public int getMaxInFlightEntries() {
                return 1;
            }
            @Override
            public int getMaxEntriesClaimed() {
                return 0;
            }
            @Override
            public PersistentQueueMode getPersistentQueueMode() {
                return PersistentQueueMode.POLLING;
            }

            @Override
            public TimeSpan getClaimedTime() {
                return null;
            }
            @Override
            public long getPollingSleepTimeMs() {
                return 0;
            }
            @Override
            public boolean isProcessingOff() {
                return false;
            }
            @Override
            public int geMaxDispatchThreads() {
                return 0;
            }
            @Override
            public int getEventQueueCapacity() {
                return 0;
            }
            @Override
            public String getTableName() {
                return "test";
            }
            @Override
            public String getHistoryTableName() {
                return null;
            }

            @Override
            public TimeSpan getReapThreshold() {
                return new TimeSpan(5, TimeUnit.MINUTES);
            }

            @Override
            public int getMaxReDispatchCount() {
                return 10;
            }
        });
        testEventBusBase = new TestEventBusBase(busService);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        busService.start();
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() throws Exception {
        busService.stop();
    }


    @Test(groups = "fast")
    public void testSimple() {
        testEventBusBase.testSimple();
    }

    @Test(groups = "fast")
    public void testDifferentType() {
        testEventBusBase.testDifferentType();
    }

    @Test(groups = "slow")
    public void testSimpleWithExceptionAndRetrySuccess() {
        testEventBusBase.testSimpleWithExceptionAndRetrySuccess();
    }

    @Test(groups = "slow")
    public void testSimpleWithExceptionAndFail() {
        testEventBusBase.testSimpleWithExceptionAndFail();
    }

}
