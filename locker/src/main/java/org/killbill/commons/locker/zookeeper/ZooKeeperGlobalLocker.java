/*
 * Copyright 2017 The Billing Project, LLC
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

package org.killbill.commons.locker.zookeeper;

import java.io.IOException;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.GlobalLockerBase;
import org.killbill.commons.locker.ResetReentrantLockCallback;

public class ZooKeeperGlobalLocker extends GlobalLockerBase implements GlobalLocker {

    public ZooKeeperGlobalLocker(final DataSource dataSource, final String lockName, final long timeout, final TimeUnit timeUnit) throws IOException, InterruptedException {
        super(dataSource, new ZooKeeperGlobalLockDao(lockName, timeout, timeUnit), timeout, timeUnit);
    }

    @Override
    protected GlobalLock getGlobalLock(final Connection connection, final String lockName, final ResetReentrantLockCallback resetCb) {
        return new ZooKeeperGlobalLock(connection, lockName, globalLockDao, resetCb);
    }

    @Override
    protected String getLockName(final String service, final String lockKey) {
        return service + "-" + lockKey;
    }
}
