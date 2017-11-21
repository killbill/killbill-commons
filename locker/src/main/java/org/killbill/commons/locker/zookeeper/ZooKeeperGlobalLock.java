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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.zookeeper.recipes.LockListener;
import org.killbill.commons.locker.zookeeper.recipes.WriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooKeeperGlobalLock implements GlobalLock {
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperGlobalLock.class);

    private final String name;
    private final String path;
    private final WriteLock writeLock;
    private final CountDownLatch lockAcquiredSignal = new CountDownLatch(1);

    private static final List<ACL> DEFAULT_ACL = ZooDefs.Ids.OPEN_ACL_UNSAFE;

    public ZooKeeperGlobalLock(String name, ZooKeeper zookeeper, String path) {
        this(name, zookeeper, path, DEFAULT_ACL);
    }

    public ZooKeeperGlobalLock(String name, ZooKeeper zookeeper, String path, List<ACL> acl) {
        this.name = name;
        this.path = path;
        writeLock = new WriteLock(zookeeper, path, acl, new SyncLockListener());
    }

    public void lock() throws InterruptedException, KeeperException {
        LOG.debug("{} requesting lock on {}...", name, path);
        writeLock.lock();
        lockAcquiredSignal.await();
    }

    public boolean lock(long timeout, TimeUnit unit) throws InterruptedException, KeeperException {
        LOG.debug("{} requesting lock on {} with timeout {} {}...", name, path, timeout, unit);
        writeLock.lock();
        return lockAcquiredSignal.await(timeout, unit);
    }

    public boolean tryLock() throws InterruptedException, KeeperException {
        return lock(1, TimeUnit.SECONDS);
    }

    public void unlock() {
        writeLock.unlock();
    }

    @Override
    public void release() {
        // TODO jgomez: define actions for this method (is it the same as the lockReleased() method below?)
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
}