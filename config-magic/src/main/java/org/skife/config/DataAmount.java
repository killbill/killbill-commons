/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.skife.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataAmount {

    private static final Pattern SPLIT = Pattern.compile("^(\\d+)\\s*([a-zA-Z]+)$");
    private static final Pattern NUM_ONLY = Pattern.compile("^(\\d+)$");
    private final long value;
    private final DataAmountUnit unit;
    private final long numBytes;

    public DataAmount(final String spec) {
        Matcher m = SPLIT.matcher(spec);
        if (!m.matches()) {
            // #7: allow undecorated unit to mean basic bytes
            m = NUM_ONLY.matcher(spec);
            if (!m.matches()) {
                throw new IllegalArgumentException(String.format("%s is not a valid data amount", spec));
            }
            unit = DataAmountUnit.BYTE;
            value = numBytes = Long.parseLong(spec);
        } else {
            final String number = m.group(1);
            final String type = m.group(2);
            this.value = Long.parseLong(number);
            this.unit = DataAmountUnit.fromString(type);
            this.numBytes = unit.getFactor() * value;
        }
    }

    public DataAmount(final long value, final DataAmountUnit unit) {
        this.value = value;
        this.unit = unit;
        this.numBytes = unit.getFactor() * value;
    }

    /**
     * @since 0.15
     */
    public DataAmount(final long rawBytes) {
        value = numBytes = rawBytes;
        unit = DataAmountUnit.BYTE;
    }

    public long getValue() {
        return value;
    }

    public DataAmountUnit getUnit() {
        return unit;
    }

    public long getNumberOfBytes() {
        return numBytes;
    }

    public DataAmount convertTo(final DataAmountUnit newUnit) {
        return new DataAmount(numBytes / newUnit.getFactor(), newUnit);
    }

    @Override
    public String toString() {
        return value + unit.getSymbol();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (numBytes ^ (numBytes >>> 32));
        result = prime * result + unit.hashCode();
        result = prime * result + (int) (value ^ (value >>> 32));
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DataAmount other = (DataAmount) obj;

        return numBytes == other.numBytes;
    }
}
