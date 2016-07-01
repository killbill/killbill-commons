/*
 * Copyright 2015-2016 Groupon, Inc
 * Copyright 2015-2016 The Billing Project, LLC
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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.killbill.commons.locker.ReentrantLock.TryAcquireLockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GlobalLockerBase implements GlobalLocker {

    protected static final long DEFAULT_TIMEOUT_MILLIS = 100L;

    protected static final Logger logger = LoggerFactory.getLogger(GlobalLockerBase.class);

    protected final GlobalLockDao globalLockDao;
    protected final long timeout;
    protected final TimeUnit timeUnit;
    protected final ReentrantLock lockTable;

    private final DataSource dataSource;

    public GlobalLockerBase(final DataSource dataSource, final GlobalLockDao globalLockDao, final long timeout, final TimeUnit timeUnit) {
        this.dataSource = dataSource;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.globalLockDao = globalLockDao;
        this.lockTable = new ReentrantLock();
    }

    @Override
    public GlobalLock lockWithNumberOfTries(final String service, final String lockKey, final int retry) throws LockFailedException {
        final String lockName = getLockName(service, lockKey);
        int tries_left = retry;
        while (tries_left-- > 0) {
            final GlobalLock lock = lock(lockName);
            if (lock != null) {
                return lock;
            }
            if (tries_left > 0) {
                sleep();
            }
        }

        logger.warn(String.format("Failed to acquire lock %s for service %s after %s retries", lockKey, service, retry));
        throw new LockFailedException();
    }

    @Override
    public boolean isFree(final String service, final String lockKey) {
        final String lockName = getLockName(service, lockKey);

        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            return globalLockDao.isLockFree(connection, lockName);
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

    protected GlobalLock lock(final String lockName) throws LockFailedException {
        final TryAcquireLockState lockState = lockTable.tryAcquireLockForExistingOwner(lockName);
        if (lockState.getLockState() == ReentrantLock.ReentrantLockState.HELD_OWNER) {
            return lockState.getOriginalLock();
        }

        if (lockState.getLockState() == ReentrantLock.ReentrantLockState.HELD_NOT_OWNER) {
            // In that case, we need to respect the provided timeout value
            try {
                Thread.sleep(TimeUnit.MILLISECONDS.convert(timeout, timeUnit));
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("lock got interrupted", e);
            }
            return null;
        }

        return doLock(lockName);
    }

    protected GlobalLock doLock(final String lockName) {
        Connection connection = null;
        boolean obtained = false;
        try {
            connection = dataSource.getConnection();
            obtained = globalLockDao.lock(connection, lockName, timeout, timeUnit);
            if (obtained) {
                final GlobalLock lock = getGlobalLock(connection, lockName, new ResetReentrantLockCallback() {
                    @Override
                    public boolean reset(final String lockName) {
                        return lockTable.releaseLock(lockName);
                    }
                });
                lockTable.createLock(lockName, lock);
                return lock;
            }
        } catch (final SQLException e) {
            logger.warn("Unable to obtain lock for {}", lockName, e);
        } finally {
            if (!obtained && connection != null) {
                try {
                    connection.close();
                } catch (final SQLException e) {
                    logger.warn("Unable to close connection", e);
                }
            }
        }
        return null;
    }

    protected abstract GlobalLock getGlobalLock(final Connection connection, final String lockName, final ResetReentrantLockCallback resetCb);

    protected abstract String getLockName(final String service, final String lockKey);

    private void sleep() {
        try {
            Thread.sleep(TimeUnit.MILLISECONDS.convert(timeout, timeUnit));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("GlobalLockerBase got interrupted", e);
        }
    }
}


