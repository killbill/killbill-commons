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

package org.killbill.commons.locker.memory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;

public class MemoryGlobalLocker implements GlobalLocker {

    private final Map<String, AtomicBoolean> locks = new ConcurrentHashMap<String, AtomicBoolean>();

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
        throw new LockFailedException();
    }

    @Override
    public synchronized boolean isFree(final String service, final String lockKey) {
        final String lockName = getLockName(service, lockKey);
        return isFree(lockName);
    }

    private synchronized Boolean isFree(final String lockName) {
        final AtomicBoolean lock = locks.get(lockName);
        return lock == null || !lock.get();
    }

    private synchronized GlobalLock lock(final String lockName) throws LockFailedException {
        if (!isFree(lockName)) {
            return null;
        }

        if (locks.get(lockName) == null) {
            locks.put(lockName, new AtomicBoolean(true));
        } else {
            locks.get(lockName).set(true);
        }

        return new GlobalLock() {
            @Override
            public void release() {
                locks.get(lockName).set(false);
            }
        };
    }

    private String getLockName(final String service, final String lockKey) {
        return service + "-" + lockKey;
    }
}
