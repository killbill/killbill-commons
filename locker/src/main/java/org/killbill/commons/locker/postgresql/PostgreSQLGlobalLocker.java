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

import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.GlobalLockerBase;
import org.killbill.commons.locker.LockFailedException;
import org.killbill.commons.locker.ResetReentrantLockCallback;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;

public class PostgreSQLGlobalLocker extends GlobalLockerBase implements GlobalLocker {


    public PostgreSQLGlobalLocker(final DataSource dataSource) {
        this(dataSource, DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    public PostgreSQLGlobalLocker(final DataSource dataSource, final long timeout, final TimeUnit timeUnit) {
        super(dataSource, new PostgreSQLGlobalLockDao(), timeout, timeUnit);
    }

    @Override
    protected GlobalLock getGlobalLock(final Connection connection, final String lockName, final ResetReentrantLockCallback resetCb) {
        return new PostgreSQLGlobalLock(connection, lockName, globalLockDao, resetCb);
    }

    protected String getLockName(final String service, final String lockKey) {
        final String lockName = service + "-" + lockKey;
        try {
            final MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            final byte[] bytes = messageDigest.digest(lockName.getBytes());
            return String.valueOf(ByteBuffer.wrap(bytes).getLong());
        } catch (final NoSuchAlgorithmException e) {
            logger.warn("Unable to allocate MessageDigest", e);
            return String.valueOf(lockName.hashCode());
        }
    }
}
