/*
 * Copyright 2017 The Billing Project, LLC
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

package org.killbill.commons.locker.zookeeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.killbill.commons.locker.GlobalLockDao;
import org.killbill.commons.locker.zookeeper.recipes.LockListener;
import org.killbill.commons.locker.zookeeper.recipes.WriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooKeeperGlobalLockDao implements GlobalLockDao {

    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperGlobalLockDao.class);
    private static final List<ACL> DEFAULT_ACL = ZooDefs.Ids.OPEN_ACL_UNSAFE;

    // TODO jgomez: we can read this property from the external properties
    private static final String zkPath = "/kb/locks/accounts/";

    private final String name;
    private final String path;
    private final WriteLock writeLock;
    private final CountDownLatch lockAcquiredSignal = new CountDownLatch(1);

    public ZooKeeperGlobalLockDao(final String lockName, final Long timeout) {
        this.name = lockName;
        this.path = zkPath + lockName;
        ZooKeeper zooKeeper = null;
        try {
            // TODO jgomez: what should we use as the connectString value? Using localhost by now
            zooKeeper = new ZooKeeper("localhost", timeout.intValue(), new Watcher() {
                @Override
                public void process(final WatchedEvent event) {
                    if (event.getState() == Event.KeeperState.SyncConnected) {
                        lockAcquiredSignal.countDown();
                    }
                }
            });
            lockAcquiredSignal.await();
        } catch (Exception e) {
            // TODO jgomez catch and throw corresponding exceptions
        }
        this.writeLock = new WriteLock(zooKeeper, path, DEFAULT_ACL, new SyncLockListener());
    }

    class SyncLockListener implements LockListener {
        @Override
        public void lockAcquired() {
            LOG.debug("Lock acquired by {} on {}", name, path);
            lockAcquiredSignal.countDown();
        }

        @Override
        public void lockReleased() {
            LOG.debug("Lock released by {} on {}", name, path);
        }
    }

    @Override
    public boolean lock(final Connection connection, final String lockName, final long timeout, final TimeUnit timeUnit) throws SQLException {
        try {
            LOG.debug("{} requesting lock on {}...", lockName, path);
            writeLock.lock();
            return lockAcquiredSignal.await(timeout, timeUnit);
        } catch (Exception e) {
            // TODO jgomez catch and throw corresponding exceptions
            return false;
        }
    }

    @Override
    public boolean releaseLock(final Connection connection, final String lockName) throws SQLException {
        // TODO jgomez: implement! Is this the unlock method?
        writeLock.unlock();
        return false;
    }

    @Override
    public boolean isLockFree(final Connection connection, final String lockName) throws SQLException {
        // TODO jgomez: implement! How to check this in the writeLock?
        return false;
    }
}
