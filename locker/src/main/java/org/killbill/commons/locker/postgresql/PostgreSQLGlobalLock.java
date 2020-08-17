/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.commons.locker.postgresql;

import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLockBase;
import org.killbill.commons.locker.GlobalLockDao;
import org.killbill.commons.locker.ResetReentrantLockCallback;

import java.sql.Connection;

public class PostgreSQLGlobalLock extends GlobalLockBase implements GlobalLock {

    public PostgreSQLGlobalLock(final Connection connection, final String lockName, final GlobalLockDao lockDao, final ResetReentrantLockCallback resetCb) {
        super(connection, lockName, lockDao, resetCb);
    }
}
