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

import org.killbill.commons.locker.GlobalLockDao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

// Note: the lock is connection specific (closing the connection releases the lock)
public class PostgreSQLGlobalLockDao implements GlobalLockDao {

    @Override
    public boolean lock(final Connection connection, final String lockName, final long timeout, final TimeUnit timeUnit) throws SQLException {
        final String sql = String.format("SELECT pg_try_advisory_lock(%s);", lockName);
        return executeLockQuery(connection, sql);
    }

    @Override
    public boolean releaseLock(final Connection connection, final String lockName) throws SQLException {
        final String sql = String.format("SELECT pg_advisory_unlock(%s);", lockName);
        return executeLockQuery(connection, sql);
    }

    @Override
    public boolean isLockFree(final Connection connection, final String lockName) throws SQLException {
        final String sql = String.format("SELECT CASE WHEN pg_try_advisory_lock(%s) THEN pg_advisory_unlock(%s) ELSE FALSE END;", lockName, lockName);
        return executeLockQuery(connection, sql);
    }

    private boolean executeLockQuery(final Connection connection, final String query) throws SQLException {
        Statement statement = null;
        try {
            statement = connection.createStatement();
            final ResultSet rs = statement.executeQuery(query);
            return rs.next() && (rs.getBoolean(1));
        } finally {
            if (statement != null) {
                statement.close();
            }
        }
    }
}
