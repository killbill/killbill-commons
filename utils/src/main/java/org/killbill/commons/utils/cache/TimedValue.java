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

class TimedValue<V> {

    private final long expireTime;
    private final V value;

    /**
     * @param timeout value in second
     */
    public TimedValue(final long timeout, final V value) {
        this.expireTime = System.currentTimeMillis() + timeout;
        this.value = value;
    }

    public boolean isTimeout() {
        final long now = System.currentTimeMillis();
        return now >= expireTime;
    }

    public V getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "TimedValue {" +
               "expireTime=" + expireTime +
               ", value=" + value +
               '}';
    }
}
