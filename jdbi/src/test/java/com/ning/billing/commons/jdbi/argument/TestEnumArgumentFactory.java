/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.commons.jdbi.argument;

import java.io.IOException;

import org.h2.jdbc.JdbcSQLException;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.commons.jdbi.JDBITestBase;

public class TestEnumArgumentFactory extends JDBITestBase {

    private enum Bier {
        hefeweizen,
        ipa
    }

    @BeforeMethod(groups = "slow")
    public void cleanupDb() throws Exception {
        cleanupDb("drop table if exists something;\n" +
                  "create table something (id int primary key, name varchar(100), bier varchar(100))");
    }

    @Test(groups = "slow")
    public void testWithoutArgument() throws IOException {
        final SomethingSqlDao somethingSqlDao = dbi.onDemand(SomethingSqlDao.class);
        try {
            somethingSqlDao.create(1, "pierre", Bier.ipa);
            Assert.fail();
        } catch (final UnableToExecuteStatementException e) {
            Assert.assertTrue(e.getCause() instanceof JdbcSQLException);
        }
    }

    @Test(groups = "slow")
    public void testWithArgument() throws Exception {
        dbi.registerArgumentFactory(new EnumArgumentFactory());

        final SomethingSqlDao somethingSqlDao = dbi.onDemand(SomethingSqlDao.class);
        final Bier bierPierre = Bier.ipa;
        somethingSqlDao.create(1, "pierre", bierPierre);
        final Bier bierStephane = Bier.hefeweizen;
        somethingSqlDao.create(2, "stephane", bierStephane);

        final String bierStringPierre = somethingSqlDao.getBier(1);
        Assert.assertEquals(bierStringPierre, bierPierre.toString());
        final String bierStringStephane = somethingSqlDao.getBier(2);
        Assert.assertEquals(bierStringStephane, bierStephane.toString());
    }

    private interface SomethingSqlDao {

        @SqlUpdate("insert into something (id, name, bier) values (:id, :name, :bier)")
        public void create(@Bind("id") final int id,
                           @Bind("name") final String name,
                           @Bind("bier") final Bier bier);

        @SqlQuery("select bier from something where id = :id")
        public String getBier(@Bind("id") final int id);
    }
}
