/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.commons.locker.redis;

import java.util.concurrent.TimeUnit;

import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.GlobalLockerBase;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public class RedisGlobalLocker extends GlobalLockerBase implements GlobalLocker {

    private final RedissonClient redissonClient;

    public RedisGlobalLocker(final String redisAddress) {
        super(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        final Config config = new Config();
        config.useSingleServer().setAddress(redisAddress);
        redissonClient = Redisson.create(config);
    }

    @Override
    public synchronized boolean isFree(final String service, final String lockKey) {
        final String lockName = getLockName(service, lockKey);
        final RLock redisLock = redissonClient.getLock(lockName);
        return !redisLock.isLocked();
    }

    @Override
    protected synchronized GlobalLock doLock(final String lockName) {
        final RLock redisLock = redissonClient.getLock(lockName);
        if (redisLock.isLocked()) {
            return null;
        }

        final boolean acquired;
        try {
            // waitTime=1ms (retry done ourselves)
            // leaseTime=5min
            acquired = redisLock.tryLock(1, 300000, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        if (!acquired) {
            return null;
        } else if (redisLock.getHoldCount() > 1) {
            // Someone beat us to it?
            redisLock.forceUnlock();
            return null;
        }

        final GlobalLock lock = new GlobalLock() {
            @Override
            public void release() {
                if (lockTable.releaseLock(lockName)) {
                    redisLock.forceUnlock();
                }
            }
        };

        lockTable.createLock(lockName, lock);

        return lock;
    }

    @Override
    protected String getLockName(final String service, final String lockKey) {
        return service + "-" + lockKey;
    }
}
