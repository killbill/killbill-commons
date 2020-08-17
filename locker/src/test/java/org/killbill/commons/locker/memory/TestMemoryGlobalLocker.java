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

package org.killbill.commons.locker.memory;

import java.io.IOException;
import java.util.UUID;

import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.killbill.commons.request.Request;
import org.killbill.commons.request.RequestData;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestMemoryGlobalLocker {

    @Test(groups = "fast")
    public void testReentrantLockInOrder() throws IOException, LockFailedException {
        final String serviceLock = "MY_LOCK_2";
        final String lockName = UUID.randomUUID().toString();

        final String requestId = "12345";

        Request.setPerThreadRequestData(new RequestData(requestId));

        final GlobalLocker locker = new MemoryGlobalLocker();
        final GlobalLock lock = locker.lockWithNumberOfTries(serviceLock, lockName, 3);
        Assert.assertFalse(locker.isFree(serviceLock, lockName));

        // Re-acquire the createLock with the same requestId, should work
        final GlobalLock reentrantLock = locker.lockWithNumberOfTries(serviceLock, lockName, 1);

        reentrantLock.release();
        Assert.assertFalse(locker.isFree(serviceLock, lockName));

        lock.release();
        Assert.assertTrue(locker.isFree(serviceLock, lockName));
    }

    @Test(groups = "fast")
    public void testReentrantLockOufOfOrder() throws IOException, LockFailedException {
        final String serviceLock = "MY_LOCK_3";
        final String lockName = UUID.randomUUID().toString();

        final String requestId = "12345";

        Request.setPerThreadRequestData(new RequestData(requestId));

        final GlobalLocker locker = new MemoryGlobalLocker();
        final GlobalLock lock = locker.lockWithNumberOfTries(serviceLock, lockName, 3);
        Assert.assertFalse(locker.isFree(serviceLock, lockName));

        // Re-acquire the createLock with the same requestId, should work
        final GlobalLock reentrantLock = locker.lockWithNumberOfTries(serviceLock, lockName, 1);

        lock.release();
        Assert.assertFalse(locker.isFree(serviceLock, lockName));

        reentrantLock.release();
        Assert.assertTrue(locker.isFree(serviceLock, lockName));
    }

    @Test(groups = "fast")
    public void testReentrantNLevelLock() throws IOException, LockFailedException {
        final String serviceLock = "MY_LOCK_N";
        final String lockName = UUID.randomUUID().toString();

        final String requestId = "44444";

        Request.setPerThreadRequestData(new RequestData(requestId));

        final GlobalLocker locker = new MemoryGlobalLocker();
        final GlobalLock lock = locker.lockWithNumberOfTries(serviceLock, lockName, 3);
        Assert.assertFalse(locker.isFree(serviceLock, lockName));

        // Re-acquire the createLock with the same requestId, should work
        final GlobalLock reentrantLock1 = locker.lockWithNumberOfTries(serviceLock, lockName, 1);

        lock.release();
        Assert.assertFalse(locker.isFree(serviceLock, lockName));

        final GlobalLock reentrantLock2 = locker.lockWithNumberOfTries(serviceLock, lockName, 1);

        reentrantLock1.release();
        Assert.assertFalse(locker.isFree(serviceLock, lockName));

        final GlobalLock reentrantLock3 = locker.lockWithNumberOfTries(serviceLock, lockName, 1);

        reentrantLock3.release();
        Assert.assertFalse(locker.isFree(serviceLock, lockName));

        reentrantLock2.release();
        Assert.assertTrue(locker.isFree(serviceLock, lockName));
    }
}
