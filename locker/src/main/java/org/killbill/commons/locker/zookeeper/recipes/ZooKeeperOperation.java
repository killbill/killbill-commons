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
package org.killbill.commons.locker.zookeeper.recipes;

import org.apache.zookeeper.KeeperException;

public interface ZooKeeperOperation {
    
    /**
     * Performs the operation - which may be involved multiple times if the connection
     * to ZooKeeper closes during this operation
     *
     * @return the result of the operation or null
     */
    boolean execute() throws KeeperException, InterruptedException;
}
