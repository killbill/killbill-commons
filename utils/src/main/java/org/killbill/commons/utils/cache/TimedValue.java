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

import java.util.Objects;

import org.killbill.commons.utils.Preconditions;

class TimedValue<V> {

    private final long expireTime;
    private final V value;

    /**
     * @param timeoutMillis timeout in millisecond
     */
    TimedValue(final long timeoutMillis, final V value) {
        this.expireTime = System.currentTimeMillis() + timeoutMillis;
        this.value = Preconditions.checkNotNull(value, "TimedValue.value cannot be null");
    }

    boolean isTimeout() {
        return System.currentTimeMillis() >= expireTime;
    }

    V getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "TimedValue {" +
               "expireTime=" + expireTime +
               ", value=" + value +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TimedValue<?> that = (TimedValue<?>) o;
        return expireTime == that.expireTime && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expireTime, value);
    }
}
