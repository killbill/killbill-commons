/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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

package org.killbill.commons.locker;

import org.killbill.commons.locker.ReentrantLock.ReentrantLockState;
import org.killbill.commons.locker.ReentrantLock.TryAcquireLockState;
import org.killbill.commons.request.Request;
import org.killbill.commons.request.RequestData;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestReentrantLock {


    private ReentrantLock lockTable;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        Request.resetPerThreadRequestData();
        lockTable = new ReentrantLock();
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() throws Exception {
        Request.resetPerThreadRequestData();
        lockTable = new ReentrantLock();
    }


    @Test(groups = "fast")
    public void testHeldByOwner() {

        Request.setPerThreadRequestData(new RequestData("12345"));

        TryAcquireLockState lockState = lockTable.tryAcquireLockForExistingOwner("foo");
        Assert.assertEquals(lockState.getLockState(), ReentrantLockState.FREE);
        // Assume we were able to get distributed lock, so the next poeration would be to createLock
        lockTable.createLock("foo", null);

        lockState = lockTable.tryAcquireLockForExistingOwner("foo");
        Assert.assertEquals(lockState.getLockState(), ReentrantLockState.HELD_OWNER);

        lockState = lockTable.tryAcquireLockForExistingOwner("foo");
        Assert.assertEquals(lockState.getLockState(), ReentrantLockState.HELD_OWNER);

        // Ref count should be 3, so last attempt should return true
        boolean free = lockTable.releaseLock("foo");
        Assert.assertFalse(free);

        free = lockTable.releaseLock("foo");
        Assert.assertFalse(free);

        free = lockTable.releaseLock("foo");
        Assert.assertTrue(free);
    }

    @Test(groups = "fast")
    public void testNotHeldByOwner() {

        Request.setPerThreadRequestData(new RequestData("12345"));

        TryAcquireLockState lockState = lockTable.tryAcquireLockForExistingOwner("bar");
        Assert.assertEquals(lockState.getLockState(), ReentrantLockState.FREE);
        // Assume we were able to get distributed lock, so the next poeration would be to createLock
        lockTable.createLock("bar", null);

        Request.setPerThreadRequestData(new RequestData("54321"));

        lockState = lockTable.tryAcquireLockForExistingOwner("bar");
        Assert.assertEquals(lockState.getLockState(), ReentrantLockState.HELD_NOT_OWNER);

        try {
            lockTable.releaseLock("foo");
            Assert.fail("Should fail to decrement lock we don't hold");
        } catch (final IllegalStateException ignore) {
        }

        Request.setPerThreadRequestData(new RequestData("12345"));

        boolean free = lockTable.releaseLock("bar");
        Assert.assertTrue(free);
    }

    @Test(groups = "fast")
    public void testInvalidCreateLock() {

        Request.setPerThreadRequestData(new RequestData("55555"));

        lockTable.createLock("bar", null);
        try {
            lockTable.createLock("bar", null);
            Assert.fail("Should fail to creating lock");
        } catch (final IllegalStateException ignore) {
        }
    }

    @Test(groups = "fast")
    public void testInvalidReleaseLock() {

        Request.setPerThreadRequestData(new RequestData("222222"));

        try {
            lockTable.releaseLock("bar");
            Assert.fail("Should fail to releasing lock");
        } catch (final IllegalStateException ignore) {
        }
    }


    @Test(groups = "fast")
    public void testWithNoRequestId() {

        // We have no requestId and nobody owns it
        TryAcquireLockState lockState = lockTable.tryAcquireLockForExistingOwner("snoopy");
        Assert.assertEquals(lockState.getLockState(), ReentrantLockState.FREE);

        Request.setPerThreadRequestData(new RequestData("33333"));
        lockTable.createLock("snoopy", null);

        Request.resetPerThreadRequestData();

        // We have no requestId but somebody owns it
        lockState = lockTable.tryAcquireLockForExistingOwner("snoopy");
        Assert.assertEquals(lockState.getLockState(), ReentrantLockState.HELD_NOT_OWNER);
    }
}
