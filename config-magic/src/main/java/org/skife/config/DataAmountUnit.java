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

public enum DataAmountUnit {
    BYTE("B", 1l),

    KIBIBYTE("KiB", 1024l),
    MEBIBYTE("MiB", 1024l * 1024l),
    GIBIBYTE("GiB", 1024l * 1024l * 1024l),
    TEBIBYTE("TiB", 1024l * 1024l * 1024l * 1024l),
    PEBIBYTE("PiB", 1024l * 1024l * 1024l * 1024l * 1024l),
    EXIBYTE("EiB", 1024l * 1024l * 1024l * 1024l * 1024l * 1024l),

    KILOBYTE("kB", 1000l),
    MEGABYTE("MB", 1000l * 1000l),
    GIGABYTE("GB", 1000l * 1000l * 1000l),
    TERABYTE("TB", 1000l * 1000l * 1000l * 1000l),
    PETABYTE("PB", 1000l * 1000l * 1000l * 1000l * 1000l),
    EXABYTE("EB", 1000l * 1000l * 1000l * 1000l * 1000l * 1000l);

    private final String symbol;
    private final long factor;

    DataAmountUnit(final String symbol, final long factor) {
        this.symbol = symbol;
        this.factor = factor;
    }

    public static DataAmountUnit fromString(final String text) {
        for (final DataAmountUnit unit : DataAmountUnit.values()) {
            if (unit.symbol.equals(text)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Unknown unit '" + text + "'");
    }

    public static DataAmountUnit fromStringCaseInsensitive(final String origText) {
        final String text = origText.toLowerCase();
        for (final DataAmountUnit unit : DataAmountUnit.values()) {
            if (unit.symbol.equals(text)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Unknown unit '" + origText + "'");
    }

    public String getSymbol() {
        return symbol;
    }

    public long getFactor() {
        return factor;
    }
}
