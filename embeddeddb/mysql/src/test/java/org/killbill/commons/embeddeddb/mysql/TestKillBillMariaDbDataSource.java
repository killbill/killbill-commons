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

package org.killbill.commons.embeddeddb.mysql;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestKillBillMariaDbDataSource {

    @Test(groups = "fast")
    public void testUpdateUrl() throws Exception {
        final KillBillMariaDbDataSource killBillMariaDbDataSource = new KillBillMariaDbDataSource();

        Assert.assertEquals(killBillMariaDbDataSource.buildUpdatedUrl("jdbc:mysql://127.0.0.1:3306/killbill?createDatabaseIfNotExist=true&allowMultiQueries=true&cachePrepStmts=false"),
                            "jdbc:mysql://127.0.0.1:3306/killbill?createDatabaseIfNotExist=true&allowMultiQueries=true&cachePrepStmts=false");
        Assert.assertEquals(killBillMariaDbDataSource.buildUpdatedUrl("jdbc:mysql://127.0.0.1:3306/killbill"),
                            "jdbc:mysql://127.0.0.1:3306/killbill");

        killBillMariaDbDataSource.setCachePrepStmts(false);
        Assert.assertEquals(killBillMariaDbDataSource.buildUpdatedUrl("jdbc:mysql://127.0.0.1:3306/killbill?createDatabaseIfNotExist=true&allowMultiQueries=true&cachePrepStmts=false"),
                            "jdbc:mysql://127.0.0.1:3306/killbill?createDatabaseIfNotExist=true&allowMultiQueries=true&cachePrepStmts=false");
        Assert.assertEquals(killBillMariaDbDataSource.buildUpdatedUrl("jdbc:mysql://127.0.0.1:3306/killbill"),
                            "jdbc:mysql://127.0.0.1:3306/killbill?cachePrepStmts=false");

        killBillMariaDbDataSource.setCachePrepStmts(true);
        Assert.assertEquals(killBillMariaDbDataSource.buildUpdatedUrl("jdbc:mysql://127.0.0.1:3306/killbill?createDatabaseIfNotExist=true&allowMultiQueries=true&cachePrepStmts=false"),
                            "jdbc:mysql://127.0.0.1:3306/killbill?createDatabaseIfNotExist=true&allowMultiQueries=true&cachePrepStmts=false");
        Assert.assertEquals(killBillMariaDbDataSource.buildUpdatedUrl("jdbc:mysql://127.0.0.1:3306/killbill"),
                            "jdbc:mysql://127.0.0.1:3306/killbill?cachePrepStmts=true");

        killBillMariaDbDataSource.setPrepStmtCacheSize(123);
        Assert.assertEquals(killBillMariaDbDataSource.buildUpdatedUrl("jdbc:mysql://127.0.0.1:3306/killbill?createDatabaseIfNotExist=true&allowMultiQueries=true&cachePrepStmts=false"),
                            "jdbc:mysql://127.0.0.1:3306/killbill?createDatabaseIfNotExist=true&allowMultiQueries=true&cachePrepStmts=false&prepStmtCacheSize=123");
        Assert.assertEquals(killBillMariaDbDataSource.buildUpdatedUrl("jdbc:mysql://127.0.0.1:3306/killbill"),
                            "jdbc:mysql://127.0.0.1:3306/killbill?cachePrepStmts=true&prepStmtCacheSize=123");
    }
}
