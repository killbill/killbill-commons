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

package com.ning.billing.commons.locker.mysql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.commons.locker.GlobalLock;
import com.ning.billing.commons.locker.GlobalLocker;
import com.ning.billing.commons.locker.LockFailedException;

public class MySqlGlobalLocker implements GlobalLocker {

    private static final Logger logger = LoggerFactory.getLogger(MySqlGlobalLocker.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 10L;

    private final MysqlGlobalLockDao mysqlGlobalLockDao = new MysqlGlobalLockDao();

    private final DataSource dataSource;
    private final long timeout;

    public MySqlGlobalLocker(final DataSource dataSource) {
        this(dataSource, DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public MySqlGlobalLocker(final DataSource dataSource, final long timeout, final TimeUnit timeUnit) {
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
            return mysqlGlobalLockDao.isLockFree(connection, lockName);
        } catch (SQLException e) {
            logger.warn("Unable to check if lock is free", e);
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.warn("Unable to close connection", e);
                }
            }
        }
    }

    private GlobalLock lock(final String lockName) throws LockFailedException {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            final boolean obtained = mysqlGlobalLockDao.lock(connection, lockName, timeout);
            if (obtained) {
                return new MysqlGlobalLock(connection, lockName);
            }
        } catch (SQLException ignored) {
        }

        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.warn("Unable to close connection", e);
            }
        }
        throw new LockFailedException();
    }

    private String getLockName(final String service, final String lockKey) {
        return service + "-" + lockKey;
    }
}
