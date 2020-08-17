/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

import org.killbill.commons.locker.GlobalLockDao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

// Note: the MySQL lock is connection specific (closing the connection releases the lock)
public class MysqlGlobalLockDao implements GlobalLockDao {

    @Override
    public boolean lock(final Connection connection, final String lockName, final long timeout, final TimeUnit timeUnit) throws SQLException {
        //
        // We pass 0 so as to not wait at all.
        // This is not optimal but mysql only supports seconds and also it make the code more readable/symetrical
        // by having the same implementation between mysql and postgreSQL (which does not have timeout)
        //
        final String sql = String.format("select GET_LOCK('%s', %d);", lockName.replace("'", "\'"), 0);
        return executeLockQuery(connection, sql);
    }

    @Override
    public boolean releaseLock(final Connection connection, final String lockName) throws SQLException {
        final String sql = String.format("select RELEASE_LOCK('%s');", lockName.replace("'", "\'"));
        return executeLockQuery(connection, sql);
    }

    @Override
    public boolean isLockFree(final Connection connection, final String lockName) throws SQLException {
        final String sql = String.format("select IS_FREE_LOCK('%s');", lockName.replace("'", "\'"));
        return executeLockQuery(connection, sql);
    }

    private boolean executeLockQuery(final Connection connection, final String query) throws SQLException {
        try (final Statement statement = connection.createStatement();
             final ResultSet rs = statement.executeQuery(query)) {
            return rs.next() && (rs.getByte(1) == 1);
        }
    }
}
