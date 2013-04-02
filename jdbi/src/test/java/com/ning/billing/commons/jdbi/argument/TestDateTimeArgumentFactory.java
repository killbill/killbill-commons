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
import java.sql.Timestamp;

import org.h2.jdbc.JdbcSQLException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.util.TimestampMapper;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.commons.jdbi.JDBITestBase;

public class TestDateTimeArgumentFactory extends JDBITestBase {

    @BeforeMethod(groups = "slow")
    public void cleanupDb() throws Exception {
        cleanupDb("drop table if exists something;\n" +
                  "create table something (id int primary key, name varchar(100), created_dt datetime)");
    }

    @Test(groups = "slow")
    public void testWithoutArgument() throws IOException {
        final SomethingSqlDao somethingSqlDao = dbi.onDemand(SomethingSqlDao.class);
        try {
            somethingSqlDao.create(1, "pierre", new DateTime(DateTimeZone.UTC));
            Assert.fail();
        } catch (final UnableToExecuteStatementException e) {
            Assert.assertTrue(e.getCause() instanceof JdbcSQLException);
        }
    }

    @Test(groups = "slow")
    public void testWithArgument() throws Exception {
        dbi.registerArgumentFactory(new DateTimeArgumentFactory());

        final SomethingSqlDao somethingSqlDao = dbi.onDemand(SomethingSqlDao.class);
        final DateTime dateTimePierre = new DateTime(2012, 10, 5, 8, 10, DateTimeZone.UTC);
        somethingSqlDao.create(1, "pierre", dateTimePierre);
        final DateTime dateTimeStephane = new DateTime(2009, 3, 1, 0, 1, DateTimeZone.UTC);
        somethingSqlDao.create(2, "stephane", dateTimeStephane);

        final Timestamp datePierre = somethingSqlDao.getCreatedDt(1);
        Assert.assertEquals(datePierre.getTime(), dateTimePierre.getMillis());
        final Timestamp dateStephane = somethingSqlDao.getCreatedDt(2);
        Assert.assertEquals(dateStephane.getTime(), dateTimeStephane.getMillis());
    }

    @RegisterMapper(TimestampMapper.class)
    private interface SomethingSqlDao {

        @SqlUpdate("insert into something (id, name, created_dt) values (:id, :name, :createdDt)")
        public void create(@Bind("id") final int id,
                           @Bind("name") final String name,
                           @Bind("createdDt") final DateTime dateTime);

        @SqlQuery("select created_dt from something where id = :id")
        public Timestamp getCreatedDt(@Bind("id") final int id);
    }
}
