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

package org.killbill.commons.utils.cache;

import java.util.function.Function;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDefaultCache {

    private static final int DEFAULT_MAX_SIZE = 3;

    private DefaultCache<Integer, String> createDefaultCache() {
        return new DefaultCache<>(DEFAULT_MAX_SIZE);
    }

    private DefaultCache<Integer, String> createDefaultCacheWithLoader(final Function<Integer, String> cacheLoader) {
        return new DefaultCache<>(DEFAULT_MAX_SIZE, DefaultCache.NO_TIMEOUT, cacheLoader);
    }

    private DefaultCache<Integer, String> createDefaultCacheWithTimeout(final int timeout) {
        return new DefaultCache<>(DEFAULT_MAX_SIZE, timeout, DefaultCache.noCacheLoader());
    }

    @Test(groups = "fast")
    public void testPut() {
        final DefaultCache<Integer, String> cache = createDefaultCache();

        cache.put(1, "A");
        cache.put(2, "B");
        cache.put(3, "C");
        cache.put(4, "D");

        Assert.assertNull(cache.map.get(1)); // null, because maxSize = 3
        Assert.assertNotNull(cache.map.get(2));
        Assert.assertNotNull(cache.map.get(3));
        Assert.assertNotNull(cache.map.get(4));
    }

    @Test(groups = "fast")
    public void testPutWithCacheLoader() {
        // Cache loader algorithm should not affected #put() operation.
        final DefaultCache<Integer, String> cache = createDefaultCacheWithLoader(key -> {
            if (key == 5) {
                return "E";
            }
            return null;
        });

        cache.put(1, "A");
        cache.put(2, "B");
        cache.put(3, "C");
        cache.put(4, "D");

        Assert.assertNull(cache.map.get(1));
        Assert.assertNotNull(cache.map.get(2));
        Assert.assertNotNull(cache.map.get(3));
        Assert.assertNotNull(cache.map.get(4));

        Assert.assertNull(cache.map.get(5)); // cache loader not playing a role here
    }

    @Test(groups = "fast")
    public void testPutWithTimeout() throws InterruptedException {
        final DefaultCache<Integer, String> cache = createDefaultCacheWithTimeout(2); // 2 sec

        cache.put(1, "A");
        cache.put(2, "B");
        cache.put(3, "C");
        cache.put(4, "D");

        Assert.assertNull(cache.map.get(1));
        Assert.assertNotNull(cache.map.get(2));
        Assert.assertNotNull(cache.map.get(3));
        Assert.assertNotNull(cache.map.get(4));

        Thread.sleep(2100);

        // call put here to trigger eviction
        cache.put(5, "E");
        Assert.assertNotNull(cache.map.get(5));

        cache.get(2); // Needed to trigger eviction
        cache.get(3); // Needed to trigger eviction
        cache.get(4); // Needed to trigger eviction

        Assert.assertNull(cache.map.get(2));
        Assert.assertNull(cache.map.get(3));
        Assert.assertNull(cache.map.get(4));
    }

    @Test(groups = "fast")
    public void testGet() {
        final DefaultCache<Integer, String> cache = createDefaultCache();

        cache.put(1, "A");
        cache.put(2, "B");
        cache.put(3, "C");
        cache.put(4, "D");

        Assert.assertNull(cache.get(1)); // removed because eldest entry
        Assert.assertNotNull(cache.get(2));
        Assert.assertNotNull(cache.get(3));
        Assert.assertNotNull(cache.get(4));
    }

    @Test(groups = "fast")
    public void testGetWithLoader() {
        final DefaultCache<Integer, String> cache = createDefaultCacheWithLoader(key -> {
            switch (key) {
                case 5: return "E";
                case 6: return "F";
            }
            return null;
        });

        cache.put(1, "A");
        cache.put(2, "B");

        Assert.assertNotNull(cache.get(1));
        Assert.assertNotNull(cache.get(2));

        Assert.assertEquals(cache.map.size(), 2); // map size not affected by loader, yet

        Assert.assertNotNull(cache.get(5));
        Assert.assertNotNull(cache.get(6));

        Assert.assertEquals(cache.map.size(), 3); // map size affected by loader, but "3" (instead of 4) because maxSize = 3.
    }

    @Test(groups = "fast")
    public void testGetWithTimeout() throws InterruptedException {
        final DefaultCache<Integer, String> cache = createDefaultCacheWithTimeout(1);

        cache.put(1, "A");
        cache.put(2, "B");

        Assert.assertNotNull(cache.get(1));
        Assert.assertNotNull(cache.get(2));
        Assert.assertNull(cache.get(3));

        Thread.sleep(1010);

        // 2, because although expired, there's no operation that removed entry, performed
        Assert.assertEquals(cache.map.size(), 2);

        // Calling get will remove entry
        Assert.assertNull(cache.get(1)); // null because expired
        Assert.assertNull(cache.get(2)); // null because expired
        Assert.assertNull(cache.get(3));

        Assert.assertEquals(cache.map.size(), 0); // All expired

        cache.put(1, "A");
        cache.put(2, "B");
        Assert.assertEquals(cache.map.size(), 2);

        Thread.sleep(1010);

        cache.put(1, "A");
        cache.get(2); // Needed to trigger eviction
        Assert.assertEquals(cache.map.size(), 1); // Because expired
    }

    @Test(groups = "fast")
    public void testGetOrLoad() {
        final DefaultCache<Integer, String> cache = createDefaultCache();
        final Function<Integer, String> loader = key -> key + "00";

        cache.put(1, "A");
        cache.put(2, "B");

        Assert.assertEquals(cache.getOrLoad(1, loader), "A");
        Assert.assertEquals(cache.getOrLoad(2, loader), "B");
        Assert.assertEquals(cache.getOrLoad(3, loader), "300");

        Assert.assertNotNull(cache.getOrLoad(1, key -> null));
        Assert.assertNotNull(cache.getOrLoad(2, key -> null));
    }

    @Test(groups = "fast")
    public void testGetOrLoadWithLoader() {
        final DefaultCache<Integer, String> cache = createDefaultCacheWithLoader(key -> {
            switch (key) {
                case 3: return "C";
                case 4: return "D";
            }
            return null;
        });
        final Function<Integer, String> localLoader = key -> {
            switch (key) {
                case 5: return "E";
                case 6: return "F";
                /*
                 * Should never showing up, as per {@link DefaultCache} javadoc: ".... cacheLoader also will take
                 * precedence over loader defined in getOrLoad(Object, Function)."
                 */
                case 4: return "D-override";
            }
            return null;
        };

        cache.put(1, "A");
        cache.put(2, "B");

        Assert.assertEquals(cache.getOrLoad(1, localLoader), "A");
        Assert.assertEquals(cache.getOrLoad(2, localLoader), "B");

        Assert.assertEquals(cache.map.size(), 2); // 2, because DefaultCache loader "load lazily"

        Assert.assertEquals(cache.getOrLoad(3, localLoader), "C");
        Assert.assertEquals(cache.getOrLoad(4, localLoader), "D");

        // 3, because DefaultCache loader loaded, but when key '4' added, cache reach max size ....
        Assert.assertEquals(cache.map.size(), 3);

        // .... and thus 1 get removed
        Assert.assertNull(cache.getOrLoad(1, localLoader));

        Assert.assertEquals(cache.getOrLoad(5, localLoader), "E");
        Assert.assertEquals(cache.getOrLoad(6, localLoader), "F");

        Assert.assertEquals(cache.map.size(), 3); // still 3 because localLoader not affected cache.

        Assert.assertNull(cache.getOrLoad(7, localLoader)); // null because 7 not defined everywhere
    }

    @Test(groups = "fast")
    public void testGetOrLoadWithTimeout() throws InterruptedException {
        final DefaultCache<Integer, String> cache = createDefaultCacheWithTimeout(1);

        final Function<Integer, String> localLoader = key -> {
            switch (key) {
                case 3: return "C";
                case 4: return "D";
            }
            return null;
        };

        cache.put(1, "A");
        cache.put(2, "B");

        Assert.assertEquals(cache.getOrLoad(1, localLoader), "A");
        Assert.assertEquals(cache.getOrLoad(2, localLoader), "B");
        Assert.assertEquals(cache.getOrLoad(3, localLoader), "C");

        Thread.sleep(1010);

        // 2, because although timeout, there's no operation that removed entry, performed
        // Also 2, because "localLoader" not added to cache
        Assert.assertEquals(cache.map.size(), 2);

        // Calling get will remove entry
        Assert.assertNull(cache.getOrLoad(1, localLoader)); // null because expired
        Assert.assertNull(cache.getOrLoad(2, localLoader)); // null because expired
        Assert.assertEquals(cache.getOrLoad(3, localLoader), "C");
        Assert.assertEquals(cache.getOrLoad(4, localLoader), "D");

        Assert.assertEquals(cache.map.size(), 0); // All expired

        cache.put(1, "A");
        cache.put(2, "B");
        Assert.assertEquals(cache.map.size(), 2);

        Thread.sleep(1010);

        cache.put(1, "A");
        cache.get(2); // Needed to trigger eviction
        Assert.assertEquals(cache.map.size(), 1); // Because expired
    }
}
