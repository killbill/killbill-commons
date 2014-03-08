/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.commons.locker.mysql;

import java.io.IOException;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.killbill.commons.embeddeddb.mysql.MySQLEmbeddedDB;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;

public class TestMysqlGlobalLocker {

    private MySQLEmbeddedDB embeddedDB;

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
        } catch (LockFailedException e) {
            gotException = true;
        }
        Assert.assertTrue(gotException);

        lock.release();
        Assert.assertTrue(locker.isFree(serviceLock, lockName));
    }
}
