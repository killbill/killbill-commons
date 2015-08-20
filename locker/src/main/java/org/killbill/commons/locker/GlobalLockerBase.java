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

package org.killbill.commons.locker;

import org.killbill.commons.request.Request;
import org.killbill.commons.request.RequestData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class GlobalLockerBase implements GlobalLocker {

    protected static final Logger logger = LoggerFactory.getLogger(GlobalLockerBase.class);

    protected final GlobalLockDao globalLockDao;

    private final DataSource dataSource;

    protected final long timeout;
    protected final TimeUnit timeUnit;

    private Map<String, String> reentrantLocks;


    public GlobalLockerBase(final DataSource dataSource, final GlobalLockDao globalLockDao, final long timeout, final TimeUnit timeUnit) {
        this.dataSource = dataSource;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.globalLockDao = globalLockDao;
        this.reentrantLocks = new HashMap<String, String>();
    }

    @Override
    public GlobalLock lockWithNumberOfTries(final String service, final String lockKey, final int retry) throws LockFailedException {
        final String lockName = getLockName(service, lockKey);

        final GlobalLock reentrantlock = getReentrantLock(lockName);
        if (reentrantlock != null) {
            logger.info("Global Lock %s already taken for request %s", lockName, Request.getPerThreadRequestData().getRequestId());
            return reentrantlock;
        }

        int tries_left = retry;
        while (tries_left-- > 0) {
            final GlobalLock lock = lock(lockName);
            if (lock != null) {
                return lock;
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

        Connection connection = null;
        boolean obtained = false;
        try {
            connection = dataSource.getConnection();
            obtained = globalLockDao.lock(connection, lockName, timeout, timeUnit);
            if (obtained) {
                setReentrantLock(lockName);
                return getGlobalLock(connection, lockName, new ResetReentrantLockCallback() {
                    @Override
                    public void reset(String lockName) {
                        resetReentrantLock(lockName);
                    }
                });
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


    protected abstract GlobalLock getGlobalLock(final Connection connection, final String lockName, final ResetReentrantLockCallback resetCb);

    protected abstract String getLockName(final String service, final String lockKey);

    private void setReentrantLock(final String lockName) {
        final RequestData requestData = Request.getPerThreadRequestData();
        if (requestData == null || requestData.getRequestId() == null) {
            return;
        }
        reentrantLocks.put(lockName, requestData.getRequestId());
    }

    private GlobalLock getReentrantLock(final String lockName) {
        // Check if we have a requestId set
        final RequestData requestData = Request.getPerThreadRequestData();
        if (requestData == null || requestData.getRequestId() == null) {
            return null;
        }

        // If we do and we already have locked it for the same requestId we return a dummy GlobalLock
        final String currentHolder = reentrantLocks.get(lockName);
        if (currentHolder != null && currentHolder.equals(requestData.getRequestId())) {
            return new GlobalLock() {
                @Override
                public void release() {
                }
            };
        }
        // If not, return null
        return null;
    }

    private void resetReentrantLock(final String lockName) {
        final RequestData requestData = Request.getPerThreadRequestData();
        if (requestData == null || requestData.getRequestId() == null) {
            return;
        }
        final String currentHolder = reentrantLocks.get(lockName);
        if (currentHolder != null && currentHolder.equals(requestData.getRequestId())) {
            reentrantLocks.remove(lockName);
        }
    }
}


