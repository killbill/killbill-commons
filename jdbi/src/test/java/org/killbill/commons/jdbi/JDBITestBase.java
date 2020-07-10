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

package org.killbill.commons.jdbi;

import org.killbill.commons.embeddeddb.h2.H2EmbeddedDB;
import org.skife.jdbi.v2.DBI;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

public abstract class JDBITestBase {

    protected H2EmbeddedDB embeddedDB;
    protected DBI dbi;

    @BeforeClass(groups = "slow")
    public void setUp() throws Exception {
        embeddedDB = new H2EmbeddedDB();
        embeddedDB.initialize();
        embeddedDB.start();
    }

    @BeforeMethod(groups = "slow")
    public void setUpMethod() throws Exception {
        dbi = new DBI(embeddedDB.getDataSource());
    }

    public void cleanupDb(final String ddl) throws Exception {
        embeddedDB.executeScript(ddl);
    }

    @AfterClass(groups = "slow")
    public void tearDown() throws Exception {
        embeddedDB.stop();
    }
}
