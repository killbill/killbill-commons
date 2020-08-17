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

package org.killbill.commons.jdbi.argument;

import java.util.UUID;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.commons.jdbi.JDBITestBase;

public class TestUUIDArgumentFactory extends JDBITestBase {

    @BeforeMethod(groups = "slow")
    public void cleanupDb() throws Exception {
        cleanupDb("drop table if exists something;\n" +
                  "create table something (id int primary key, name varchar(100), unique_id varchar(100))");
    }

    @Test(groups = "slow")
    public void testWithArgument() throws Exception {
        dbi.registerArgumentFactory(new UUIDArgumentFactory());

        final SomethingSqlDao somethingSqlDao = dbi.onDemand(SomethingSqlDao.class);
        final UUID idPierre = UUID.randomUUID();
        somethingSqlDao.create(1, "pierre", idPierre);
        final UUID idStephane = UUID.randomUUID();
        somethingSqlDao.create(2, "stephane", idStephane);

        final String idStringPierre = somethingSqlDao.getUniqueId(1);
        Assert.assertEquals(idStringPierre, idPierre.toString());
        final String idStringStephane = somethingSqlDao.getUniqueId(2);
        Assert.assertEquals(idStringStephane, idStephane.toString());
    }

    private interface SomethingSqlDao {

        @SqlUpdate("insert into something (id, name, unique_id) values (:id, :name, :uniqueId)")
        public void create(@Bind("id") final int id,
                           @Bind("name") final String name,
                           @Bind("uniqueId") final UUID uniqueId);

        @SqlQuery("select unique_id from something where id = :id")
        public String getUniqueId(@Bind("id") final int id);
    }
}
