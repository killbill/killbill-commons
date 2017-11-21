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

/**
 * Copied directly from the ZooKeeper lock recipes, and modified slightly (e.g. for Sonar rule violations).
 *
 * This class has two methods which are call
 * back methods when a lock is acquired and 
 * when the lock is released.
 *
 */
public interface LockListener {
    /**
     * call back called when the lock 
     * is acquired
     */
    void lockAcquired();
    
    /**
     * call back called when the lock is 
     * released.
     */
    void lockReleased();
}