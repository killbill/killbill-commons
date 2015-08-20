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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class GlobalLockBase implements GlobalLock {


    private static final Logger logger = LoggerFactory.getLogger(GlobalLockBase.class);

    private final GlobalLockDao lockDao;

    private final Connection connection;
    private final String lockName;
    private final ResetReentrantLockCallback resetCallback;


    public GlobalLockBase(final Connection connection, final String lockName, final GlobalLockDao lockDao, final ResetReentrantLockCallback resetCallback) {
        this.lockDao = lockDao;
        this.connection = connection;
        this.lockName = lockName;
        this.resetCallback = resetCallback;
    }

    @Override
    public void release() {
        try {
            if (resetCallback != null) {
                resetCallback.reset(lockName);
            }
            lockDao.releaseLock(connection, lockName);
        } catch (final SQLException e) {
            logger.warn("Unable to release lock for " + lockName, e);
        } finally {
            try {
                connection.close();
            } catch (final SQLException e) {
                logger.warn("Unable to close connection", e);
            }
        }
    }
}
