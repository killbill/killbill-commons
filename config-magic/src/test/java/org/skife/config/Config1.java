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

interface Config1 {

    @Config("stringOption")
    String getStringOption();

    @Config("booleanOption")
    boolean getBooleanOption();

    @Config("boxedBooleanOption")
    Boolean getBoxedBooleanOption();

    @Config("byteOption")
    byte getByteOption();

    @Config("boxedByteOption")
    Byte getBoxedByteOption();

    @Config("shortOption")
    short getShortOption();

    @Config("boxedShortOption")
    Short getBoxedShortOption();

    @Config("integerOption")
    int getIntegerOption();

    @Config("boxedIntegerOption")
    Integer getBoxedIntegerOption();

    @Config("longOption")
    long getLongOption();

    @Config("boxedLongOption")
    Long getBoxedLongOption();

    @Config("floatOption")
    float getFloatOption();

    @Config("boxedFloatOption")
    Float getBoxedFloatOption();

    @Config("doubleOption")
    double getDoubleOption();

    @Config("boxedDoubleOption")
    Double getBoxedDoubleOption();
}
