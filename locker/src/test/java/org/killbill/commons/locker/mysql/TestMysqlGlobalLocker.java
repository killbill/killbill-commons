/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.commons.locker.mysql;

import org.killbill.commons.embeddeddb.mysql.MySQLEmbeddedDB;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.killbill.commons.request.Request;
import org.killbill.commons.request.RequestData;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.UUID;

public class TestMysqlGlobalLocker {

    private MySQLEmbeddedDB embeddedDB;

    @BeforeMethod(groups = "mysql")
    public void beforeMethod() throws Exception {
        Request.resetPerThreadRequestData();
    }

    @AfterMethod(groups = "mysql")
    public void afterMethod() throws Exception {
        Request.resetPerThreadRequestData();
    }

    @BeforeClass(groups = "mysql")
    public void setUp() throws Exception {
        embeddedDB = new MySQLEmbeddedDB();
        embeddedDB.initialize();
        embeddedDB.start();
    }

    @AfterClass(groups = "mysql")
    public void tearDown() throws Exception {
        embeddedDB.stop();
    }

    @Test(groups = "mysql")
    public void testSimpleLocking() throws IOException, LockFailedException {
        final String serviceLock = "MY_AWESOME_LOCK";
        final String lockName = UUID.randomUUID().toString();

        final GlobalLocker locker = new MySqlGlobalLocker(embeddedDB.getDataSource());
        final GlobalLock lock = locker.lockWithNumberOfTries(serviceLock, lockName, 3);
        Assert.assertFalse(locker.isFree(serviceLock, lockName));

        boolean gotException = false;
        try {
            locker.lockWithNumberOfTries(serviceLock, lockName, 1);
        } catch (final LockFailedException e) {
            gotException = true;
        }
        Assert.assertTrue(gotException);

        lock.release();
        Assert.assertTrue(locker.isFree(serviceLock, lockName));
    }


    @Test(groups = "mysql")
    public void testReentrantLock() throws IOException, LockFailedException {
        final String serviceLock = "MY_SHITTY_LOCK";
        final String lockName = UUID.randomUUID().toString();

        final String requestId = "12345";

        Request.setPerThreadRequestData(new RequestData(requestId));

        final GlobalLocker locker = new MySqlGlobalLocker(embeddedDB.getDataSource());
        final GlobalLock lock = locker.lockWithNumberOfTries(serviceLock, lockName, 3);
        Assert.assertFalse(locker.isFree(serviceLock, lockName));
        Assert.assertTrue(lock instanceof MysqlGlobalLock);

        // Re-aquire the lock with the same requestId, should work
        final GlobalLock reentrantLock = locker.lockWithNumberOfTries(serviceLock, lockName, 1);
        Assert.assertFalse(reentrantLock instanceof MysqlGlobalLock);

        lock.release();
        Assert.assertTrue(locker.isFree(serviceLock, lockName));
    }

}
