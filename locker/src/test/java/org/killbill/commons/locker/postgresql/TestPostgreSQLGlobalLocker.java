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

package org.killbill.commons.locker.postgresql;

import java.io.IOException;
import java.util.UUID;

import org.killbill.commons.embeddeddb.postgresql.PostgreSQLEmbeddedDB;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TestPostgreSQLGlobalLocker {

    private PostgreSQLEmbeddedDB embeddedDB;

    @BeforeClass(groups = "postgresql")
    public void setUp() throws Exception {
        embeddedDB = new PostgreSQLEmbeddedDB();
        embeddedDB.initialize();
        embeddedDB.start();
    }

    @AfterClass(groups = "postgresql")
    public void tearDown() throws Exception {
        embeddedDB.stop();
    }

    @Test(groups = "postgresql")
    public void testSimpleLocking() throws IOException, LockFailedException {
        final String serviceLock = "MY_AWESOME_LOCK";
        final String lockName = UUID.randomUUID().toString();

        final GlobalLocker locker = new PostgreSQLGlobalLocker(embeddedDB.getDataSource());
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
}
