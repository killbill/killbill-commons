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

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostgreSQLGlobalLocker implements GlobalLocker {

    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLGlobalLocker.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 1L;

    private final PostgreSQLGlobalLockDao lockDao = new PostgreSQLGlobalLockDao();

    private final DataSource dataSource;
    private final long timeout;

    public PostgreSQLGlobalLocker(final DataSource dataSource) {
        this(dataSource, DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public PostgreSQLGlobalLocker(final DataSource dataSource, final long timeout, final TimeUnit timeUnit) {
        this.dataSource = dataSource;
        this.timeout = TimeUnit.SECONDS.convert(timeout, timeUnit);
    }

    @Override
    public GlobalLock lockWithNumberOfTries(final String service, final String lockKey, final int retry) throws LockFailedException {
        final String lockName = getLockName(service, lockKey);

        int tries_left = retry;
        while (tries_left-- > 0) {
            final GlobalLock lock = lock(lockName);
            if (lock != null) {
                return lock;
            } else if (tries_left > 0) {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(timeout));
                } catch (final InterruptedException e) {

                }
            }
        }

        logger.warn(String.format("Failed to acquire lock %s for service %s after %d retries", lockKey, service, retry));
        throw new LockFailedException();
    }

    @Override
    public boolean isFree(final String service, final String lockKey) {
        final String lockName = getLockName(service, lockKey);

        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            return lockDao.isLockFree(connection, lockName);
        } catch (final SQLException e) {
            logger.warn("Unable to check if lock is free", e);
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (final SQLException e) {
                    logger.warn("Unable to close connection", e);
                }
            }
        }
    }

    private GlobalLock lock(final String lockName) throws LockFailedException {
        Connection connection = null;
        boolean obtained = false;
        try {
            connection = dataSource.getConnection();
            obtained = lockDao.lock(connection, lockName);
            if (obtained) {
                return new PostgreSQLGlobalLock(connection, lockName);
            }
        } catch (final SQLException e) {
            logger.warn("Unable to obtain lock for " + lockName, e);
        } finally {
            if (!obtained) {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (final SQLException e) {
                        logger.warn("Unable to close connection", e);
                    }
                }
            }
        }
        return null;
    }

    private String getLockName(final String service, final String lockKey) {
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
