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

import java.io.IOException;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.GlobalLockerBase;
import org.killbill.commons.locker.ResetReentrantLockCallback;

public class ZooKeeperGlobalLocker extends GlobalLockerBase implements GlobalLocker {

    final CountDownLatch connectedSignal = new CountDownLatch(1);
    protected static final String zkPath = "/kb/locks/accounts/";

    public ZooKeeperGlobalLocker() {
        super(null, null, DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Override
    protected GlobalLock getGlobalLock(final Connection connection, final String lockName, final ResetReentrantLockCallback resetCb) {
        try {
            // TODO jgomez 1: use the same timeout value?
            ZooKeeper zooKeeper = new ZooKeeper("connectString", Long.valueOf(timeout).intValue(), new Watcher() {
                @Override
                public void process(final WatchedEvent event) {
                    if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                        connectedSignal.countDown();
                    }
                }
            });
            // TODO jgomez 2: what should be the Lock Name?
            // TODO jgomez 3: where do I get the accountId from commons?
            return new ZooKeeperGlobalLock("ZooKeeper Lock", zooKeeper, zkPath + "<account_id>");
        } catch (IOException e) {
            // TODO jgomez 4: handle errors and throw corresponding exceptions!
            return null;
        }
    }

    @Override
    protected String getLockName(final String service, final String lockKey) {
        return service + "-" + lockKey;
    }
}
