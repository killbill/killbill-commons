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
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.commons.jdbi.JDBITestBase;

public class TestDateTimeZoneArgumentFactory extends JDBITestBase {

    @BeforeMethod(groups = "slow")
    public void cleanupDb() throws Exception {
        cleanupDb("drop table if exists something;\n" +
                  "create table something (id int primary key, name varchar(100), created_tz varchar(100))");
    }

    @Test(groups = "slow")
    public void testWithoutArgument() throws IOException {
        final SomethingSqlDao somethingSqlDao = dbi.onDemand(SomethingSqlDao.class);
        try {
            somethingSqlDao.create(1, "pierre", DateTimeZone.UTC);
            Assert.fail();
        } catch (final UnableToExecuteStatementException e) {
            Assert.assertTrue(e.getCause() instanceof JdbcSQLDataException);
        }
    }

    @Test(groups = "slow")
    public void testWithArgument() throws Exception {
        dbi.registerArgumentFactory(new DateTimeZoneArgumentFactory());

        final SomethingSqlDao somethingSqlDao = dbi.onDemand(SomethingSqlDao.class);
        final DateTimeZone dateTimeZonePierre = DateTimeZone.UTC;
        somethingSqlDao.create(1, "pierre", dateTimeZonePierre);
        final DateTimeZone dateTimeZoneStephane = DateTimeZone.forID("Europe/London");
        somethingSqlDao.create(2, "stephane", dateTimeZoneStephane);

        final String tzStringPierre = somethingSqlDao.getCreatedTZ(1);
        Assert.assertEquals(tzStringPierre, dateTimeZonePierre.toString());
        final String tzStringStephane = somethingSqlDao.getCreatedTZ(2);
        Assert.assertEquals(tzStringStephane, dateTimeZoneStephane.toString());
    }

    private interface SomethingSqlDao {

        @SqlUpdate("insert into something (id, name, created_tz) values (:id, :name, :createdTZ)")
        public void create(@Bind("id") final int id,
                           @Bind("name") final String name,
                           @Bind("createdTZ") final DateTimeZone dateTimeZone);

        @SqlQuery("select created_tz from something where id = :id")
        public String getCreatedTZ(@Bind("id") final int id);
    }
}
