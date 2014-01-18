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

package com.ning.billing.bus;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.TestSetup;
import com.ning.billing.bus.api.PersistentBus;

public class TestPersistentEventBus extends TestSetup {

    private TestEventBusBase testEventBusBase;
    private PersistentBus busService;

    @Override
    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        super.beforeClass();
        busService = new DefaultPersistentBus(getDBI(), clock, getPersistentBusConfig(), metricRegistry);
        testEventBusBase = new TestEventBusBase(busService);
    }

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        busService.start();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        busService.stop();
    }


    @Test(groups = "slow")
    public void testSimple() {
        testEventBusBase.testSimple();
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
