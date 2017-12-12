/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.bus;

import org.killbill.TestSetup;
import org.killbill.bus.api.BusEventWithMetadata;
import org.killbill.bus.api.PersistentBus;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.Iterables;

public class TestPersistentEventBus extends TestSetup {

    private TestEventBusBase testEventBusBase;
    private PersistentBus busService;

    @Override
    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        super.beforeClass();
    }

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        // Reinitialize to restart the pool
        busService = new DefaultPersistentBus(getDBI(), clock, getPersistentBusConfig(), metricRegistry, databaseTransactionNotificationApi);
        testEventBusBase = new TestEventBusBase(busService);
        busService.start();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        busService.stop();
    }

    @Test(groups = "slow")
    public void testSimple() {
        assertNoInProcessingEvent();
        testEventBusBase.testSimple();
        assertNoInProcessingEvent();
    }

    @Test(groups = "slow")
    public void testSimpleWithExceptionAndRetrySuccess() {
        assertNoInProcessingEvent();
        testEventBusBase.testSimpleWithExceptionAndRetrySuccess();
        assertNoInProcessingEvent();
    }

    @Test(groups = "slow")
    public void testSimpleWithExceptionAndFail() {
        assertNoInProcessingEvent();
        testEventBusBase.testSimpleWithExceptionAndFail();
        assertNoInProcessingEvent();
    }

    private void assertNoInProcessingEvent() {
        Assert.assertEquals(Iterables.<BusEventWithMetadata>size(busService.getInProcessingBusEvents()), 0);
        Assert.assertEquals(busService.getNbReadyEntries(clock.getUTCNow()), 0);
    }
}
