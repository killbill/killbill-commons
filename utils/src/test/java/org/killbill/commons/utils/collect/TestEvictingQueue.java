/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.commons.utils.collect;

import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestEvictingQueue {

    private static final int MAX_SIZE = 100;

    private EvictingQueue<Integer> createEvictingQueue() {
        final EvictingQueue<Integer> result = new EvictingQueue<>(MAX_SIZE);
        for (int i = 0; i < (MAX_SIZE - 3); i++) {
            result.add(i);
        }
        return result;
    }

    @Test(groups = "fast", description = "Test several methods to make it more useful")
    public void testAddPeekSizeContains() {
        final EvictingQueue<Integer> evictingQueue = createEvictingQueue();

        Assert.assertEquals(evictingQueue.peek(), 0);

        evictingQueue.add(201);

        Assert.assertEquals(evictingQueue.size(), 98);
        Assert.assertEquals(evictingQueue.peek(), 0); // Peek keep 0 (not reached limit)
        Assert.assertTrue(evictingQueue.contains(0));
        Assert.assertTrue(evictingQueue.contains(1));

        evictingQueue.add(202);
        evictingQueue.add(203);

        Assert.assertEquals(evictingQueue.size(), MAX_SIZE);
        Assert.assertEquals(evictingQueue.peek(), 0); // Peek keep 0 (not reached limit)
        Assert.assertTrue(evictingQueue.contains(0));
        Assert.assertTrue(evictingQueue.contains(1));

        evictingQueue.add(204);
        evictingQueue.add(205);

        Assert.assertEquals(evictingQueue.size(), MAX_SIZE);
        Assert.assertEquals(evictingQueue.peek(), 2); // (reach limit peek changes)
        Assert.assertFalse(evictingQueue.contains(0));
        Assert.assertFalse(evictingQueue.contains(1));
        Assert.assertTrue(evictingQueue.contains(2));
        Assert.assertTrue(evictingQueue.contains(3));

        evictingQueue.add(206);
        evictingQueue.add(207);

        Assert.assertEquals(evictingQueue.size(), MAX_SIZE);
        Assert.assertEquals(evictingQueue.peek(), 4); // (reach limit peek changes)
        Assert.assertFalse(evictingQueue.contains(2));
        Assert.assertFalse(evictingQueue.contains(3));
        Assert.assertTrue(evictingQueue.contains(4));
        Assert.assertTrue(evictingQueue.contains(5));
    }

    @Test(groups = "fast")
    public void testRemainingCapacity() {
        final EvictingQueue<Integer> evictingQueue = createEvictingQueue();

        evictingQueue.add(201);
        Assert.assertEquals(evictingQueue.remainingCapacity(), 2);

        evictingQueue.add(202);
        Assert.assertEquals(evictingQueue.remainingCapacity(), 1);

        evictingQueue.add(203);
        Assert.assertEquals(evictingQueue.remainingCapacity(), 0);

        evictingQueue.clear();

        Assert.assertEquals(evictingQueue.remainingCapacity(), 100);
    }

    @Test(groups = "fast")
    public void testAddAll() {
        final EvictingQueue<Integer> evictingQueue = createEvictingQueue();
        evictingQueue.addAll(List.of(201, 202));

        Assert.assertEquals(evictingQueue.size(), 99);
        Assert.assertEquals(evictingQueue.remainingCapacity(), 1);

        evictingQueue.add(100);

        Assert.assertEquals(evictingQueue.size(), 100);
        Assert.assertEquals(evictingQueue.remainingCapacity(), 0);

        evictingQueue.addAll(List.of(204, 205, 206));

        Assert.assertEquals(evictingQueue.size(), 100);
        Assert.assertEquals(evictingQueue.remainingCapacity(), 0);
    }
}
