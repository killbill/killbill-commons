/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.bus;

import java.util.UUID;

public abstract class DefaultBusPersistentEvent {


    private final UUID userToken;
    private final Long searchKey2;
    private final Long searchKey1;

    public DefaultBusPersistentEvent(final UUID userToken, final Long searchKey1, final Long searchKey2) {
        this.userToken = userToken;
        this.searchKey2 = searchKey2;
        this.searchKey1 = searchKey1;
    }
}
