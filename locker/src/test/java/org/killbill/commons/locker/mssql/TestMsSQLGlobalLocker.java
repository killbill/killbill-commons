/*
 * Copyright 2014-2020 Groupon, Inc
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

package org.killbill.commons.locker.mssql;

import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.killbill.commons.embeddeddb.mssql.MsSQLEmbeddedDB;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.killbill.commons.locker.postgresql.PostgreSQLGlobalLock;
import org.killbill.commons.locker.postgresql.PostgreSQLGlobalLocker;
import org.killbill.commons.request.Request;
import org.killbill.commons.request.RequestData;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestMsSQLGlobalLocker {

    private MsSQLEmbeddedDB  msSQLEmbeddedDB;
    private Map transactionLockMap;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        Request.resetPerThreadRequestData();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        Request.resetPerThreadRequestData();
    }

    @BeforeClass(groups = "fast")
    public void setUp() throws Exception {
        msSQLEmbeddedDB = new MsSQLEmbeddedDB("TestDB", "sa","Msqlpassword1#");
        msSQLEmbeddedDB.initialize();
        msSQLEmbeddedDB.start();
        transactionLockMap = msSQLEmbeddedDB.initTransactions(); // returns two locks, T1, T2. T1 blocking T2
        Assert.assertNotNull(transactionLockMap);
        transactionLockMap.forEach((o, o2) -> {
            System.out.println(String.format("Lock Name %s, lock session_id %s", o.toString(), o2.toString()));
        });
    }

    @AfterClass(groups = "fast")
    public void tearDown() throws Exception {
        msSQLEmbeddedDB.stop();
    }


    @Test(groups = "fast")
    public void testReleaseLock() throws IOException {
        final String serviceLock = "MY_SQLSERVER_RELEASE_LOCK";
        final String lockName = (String) transactionLockMap.get("T1");


        final GlobalLocker locker = new MsSQLGlobalLocker(msSQLEmbeddedDB.getDataSource());
        Assert.assertFalse(locker.isFree(serviceLock, lockName)); //the lock state should still be locked, so not free

        boolean lockReleased = true;
        try {
            GlobalLock lock = locker.lockWithNumberOfTries(serviceLock, lockName, 1); //acquire lock
            System.out.println("Release lock session id : "+lockName);
            lock.release(); //release lock
            Assert.assertTrue(lock instanceof MsSQLGlobalLock);
        } catch (final LockFailedException e) {
            e.printStackTrace();
            lockReleased = false;
        }
        Assert.assertTrue(lockReleased);

    }

    @Test(groups = "slow")
    public void testAcquireLock() throws IOException, LockFailedException {
        Assert.assertNotNull(transactionLockMap.get("T1"));
        Assert.assertNull(transactionLockMap.get("T2"));

        final String serviceLock = "MY_SQLSERVER_ACQUIRE_LOCK";
        final String lockName = String.valueOf(transactionLockMap.get("T1"));

        final GlobalLocker locker = new MsSQLGlobalLocker(msSQLEmbeddedDB.getDataSource());
        final GlobalLock lock = locker.lockWithNumberOfTries(serviceLock, lockName, 3); //checks a lock here that blocks a transaction
        Assert.assertTrue(lock instanceof MsSQLGlobalLock);
        Assert.assertFalse(locker.isFree(serviceLock, lockName)); //the lock state indicate an upper level lock
    }

    @Test(groups = "slow")
    public void testInvalidCreateLock() throws IOException {
        Assert.assertNotNull(transactionLockMap.get("T1"));
        Assert.assertNull(transactionLockMap.get("T2"));

        final String serviceLock = "MY_SQLSERVER_ACQUIRE_LOCK";
        final String session = String.valueOf(transactionLockMap.get("T2"));
        final String lockName = session == null ? "0" : session; //should be blocked by T1 so fails to get a lock

        final GlobalLocker locker = new MsSQLGlobalLocker(msSQLEmbeddedDB.getDataSource());
        boolean isLockCreated;
        GlobalLock lock = null;
        try{
             lock = locker.lockWithNumberOfTries(serviceLock, lockName, 3); //this should fail
            isLockCreated = true;
        }catch (Exception ex){
            isLockCreated = false;
        }
        Assert.assertFalse(isLockCreated);
        Assert.assertNull(lock);
    }
}
