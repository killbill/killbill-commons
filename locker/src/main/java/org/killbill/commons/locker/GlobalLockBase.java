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

package org.killbill.commons.locker;

import java.sql.Connection;
import java.sql.SQLException;

import org.killbill.commons.profiling.Profiling;
import org.killbill.commons.profiling.Profiling.WithProfilingCallback;
import org.killbill.commons.profiling.ProfilingFeature.ProfilingFeatureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalLockBase implements GlobalLock {


    private static final Logger logger = LoggerFactory.getLogger(GlobalLockBase.class);

    private final GlobalLockDao lockDao;

    private final Connection connection;
    private final String lockName;
    private final ResetReentrantLockCallback resetCallback;
    private final Profiling<Void, RuntimeException> prof;


    public GlobalLockBase(final Connection connection, final String lockName, final GlobalLockDao lockDao, final ResetReentrantLockCallback resetCallback) {
        this.lockDao = lockDao;
        this.connection = connection;
        this.lockName = lockName;
        this.resetCallback = resetCallback;
        this.prof = new Profiling<Void, RuntimeException>();
    }

    @Override
    public void release() {
        prof.executeWithProfiling(ProfilingFeatureType.GLOCK, "release", new WithProfilingCallback<Void, RuntimeException>() {
            @Override
            public Void execute() throws RuntimeException {

                if (resetCallback != null && !resetCallback.reset(lockName)) {
                    // We are not the last one using that lock, bail early (AND don't close the connection)...
                    return null;
                }
                try {
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
                return null;
            }
        });
    }
}
