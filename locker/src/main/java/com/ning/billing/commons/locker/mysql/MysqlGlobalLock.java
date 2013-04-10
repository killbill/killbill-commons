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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.commons.locker.GlobalLock;

public class MysqlGlobalLock implements GlobalLock {

    private static final Logger logger = LoggerFactory.getLogger(GlobalLock.class);

    private final MysqlGlobalLockDao mysqlGlobalLockDao = new MysqlGlobalLockDao();

    private final Connection connection;
    private final String lockName;

    public MysqlGlobalLock(final Connection connection, final String lockName) {
        this.connection = connection;
        this.lockName = lockName;
    }

    @Override
    public void release() {
        try {
            mysqlGlobalLockDao.releaseLock(connection, lockName);
        } catch (SQLException e) {
            logger.warn("Unable to release lock", e);
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.warn("Unable to close connection", e);
            }
        }
    }
}
