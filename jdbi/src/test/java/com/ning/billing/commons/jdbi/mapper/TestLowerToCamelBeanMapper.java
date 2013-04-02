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

package com.ning.billing.commons.jdbi.mapper;

import java.io.IOException;

import org.skife.jdbi.v2.exceptions.DBIException;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.commons.jdbi.JDBITestBase;

public class TestLowerToCamelBeanMapper extends JDBITestBase {

    @BeforeMethod(groups = "slow")
    public void cleanupDb() throws Exception {
        cleanupDb("drop table if exists something;\n" +
                  "create table something (id int primary key, lower_cased_field varchar(100), another_lower_cased_field int)");
    }

    @Test(groups = "slow")
    public void testWithoutArgument() throws IOException {
        final SomethingSqlDao somethingSqlDao = dbi.onDemand(SomethingSqlDao.class);
        somethingSqlDao.create(1, "pierre", 12);

        try {
            somethingSqlDao.getSomething(1);
            Assert.fail();
        } catch (final DBIException e) {
            Assert.assertEquals(e.getMessage(), "No mapper registered for com.ning.billing.commons.jdbi.mapper.TestLowerToCamelBeanMapper$SomethingBean");
        }
    }

    @Test(groups = "slow")
    public void testWithArgument() throws Exception {
        dbi.registerMapper(new LowerToCamelBeanMapperFactory(SomethingBean.class));

        final SomethingSqlDao somethingSqlDao = dbi.onDemand(SomethingSqlDao.class);
        final int fieldBPierre = Integer.MAX_VALUE;
        somethingSqlDao.create(1, "pierre", fieldBPierre);
        final int fieldBStephane = 29361;
        somethingSqlDao.create(2, "stephane", fieldBStephane);

        final SomethingBean foundPierre = somethingSqlDao.getSomething(1);
        Assert.assertEquals(foundPierre.getLowerCasedField(), "pierre");
        Assert.assertEquals(foundPierre.getAnotherLowerCasedField(), fieldBPierre);
        final SomethingBean foundStephane = somethingSqlDao.getSomething(2);
        Assert.assertEquals(foundStephane.getLowerCasedField(), "stephane");
        Assert.assertEquals(foundStephane.getAnotherLowerCasedField(), fieldBStephane);
    }

    private interface SomethingSqlDao {

        @SqlUpdate("insert into something (id, lower_cased_field, another_lower_cased_field) values (:id, :fieldA, :fieldB)")
        public void create(@Bind("id") final int id,
                           @Bind("fieldA") final String fieldA,
                           @Bind("fieldB") final int fieldB);

        @SqlQuery("select lower_cased_field, another_lower_cased_field from something where id = :id")
        public SomethingBean getSomething(@Bind("id") final int id);
    }

    // Needs to be public for the reflection magic
    public static final class SomethingBean {

        private String lowerCasedField;
        private long anotherLowerCasedField;

        public String getLowerCasedField() {
            return lowerCasedField;
        }

        public long getAnotherLowerCasedField() {
            return anotherLowerCasedField;
        }
    }
}
