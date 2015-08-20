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

import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.GlobalLockerBase;
import org.killbill.commons.locker.ResetReentrantLockCallback;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;

public class MySqlGlobalLocker extends GlobalLockerBase implements GlobalLocker {

    private static final long DEFAULT_TIMEOUT_SECONDS = 10L;

    public MySqlGlobalLocker(final DataSource dataSource) {
        this(dataSource, DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public MySqlGlobalLocker(final DataSource dataSource, final long timeout, final TimeUnit timeUnit) {
        super(dataSource, new MysqlGlobalLockDao(), timeout, timeUnit);
    }

    @Override
    protected GlobalLock getGlobalLock(Connection connection, String lockName, final ResetReentrantLockCallback resetCb) {
        return new MysqlGlobalLock(connection, lockName, globalLockDao, resetCb);
    }

    protected String getLockName(final String service, final String lockKey) {
        return service + "-" + lockKey;
    }
}
