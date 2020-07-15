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

import java.io.IOException;

import org.h2.jdbc.JdbcSQLDataException;
import org.h2.jdbc.JdbcSQLException;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.commons.jdbi.JDBITestBase;

public class TestLocalDateArgumentFactory extends JDBITestBase {

    @BeforeMethod(groups = "slow")
    public void cleanupDb() throws Exception {
        cleanupDb("drop table if exists something;\n" +
                  "create table something (id int primary key, name varchar(100), created_dt varchar(100))");
    }

    @Test(groups = "slow")
    public void testWithoutArgument() throws IOException {
        final SomethingSqlDao somethingSqlDao = dbi.onDemand(SomethingSqlDao.class);
        try {
            somethingSqlDao.create(1, "pierre", new LocalDate(DateTimeZone.UTC));
            Assert.fail();
        } catch (final UnableToExecuteStatementException e) {
            Assert.assertTrue(e.getCause() instanceof JdbcSQLDataException);
        }
    }

    @Test(groups = "slow")
    public void testWithArgument() throws Exception {
        dbi.registerArgumentFactory(new LocalDateArgumentFactory());

        final SomethingSqlDao somethingSqlDao = dbi.onDemand(SomethingSqlDao.class);
        final LocalDate localDatePierre = new LocalDate(2012, 5, 12);
        somethingSqlDao.create(1, "pierre", localDatePierre);
        final LocalDate localDateStephane = new LocalDate(2009, 3, 9);
        somethingSqlDao.create(2, "stephane", localDateStephane);

        final String localDateStringPierre = somethingSqlDao.getCreatedDt(1);
        Assert.assertEquals(localDateStringPierre, localDatePierre.toString());
        final String localDateStringStephane = somethingSqlDao.getCreatedDt(2);
        Assert.assertEquals(localDateStringStephane, localDateStephane.toString());
    }

    private interface SomethingSqlDao {

        @SqlUpdate("insert into something (id, name, created_dt) values (:id, :name, :createdDt)")
        public void create(@Bind("id") final int id,
                           @Bind("name") final String name,
                           @Bind("createdDt") final LocalDate createdDt);

        @SqlQuery("select created_dt from something where id = :id")
        public String getCreatedDt(@Bind("id") final int id);
    }
}
