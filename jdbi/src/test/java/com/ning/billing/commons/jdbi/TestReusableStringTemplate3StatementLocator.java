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

package com.ning.billing.commons.jdbi;

import java.io.IOException;

import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestReusableStringTemplate3StatementLocator extends JDBITestBase {

    private static final String TABLE_NAME = "something";

    @BeforeMethod(groups = "slow")
    public void cleanupDb() throws Exception {
        cleanupDb("drop table if exists " + TABLE_NAME + ";\n" +
                  "create table " + TABLE_NAME + " (id int primary key)");

        // This will break - as it generates the following query on the second invocation:
        //   delete from somethingsomething
        //dbi.setStatementLocator(new StringTemplate3StatementLocator(SomethingLiteralSqlDao.class, true, true));
    }

    @Test(groups = "slow")
    public void testMultipleInvocationsWithoutLiterals() throws IOException {
        dbi.setStatementLocator(new ReusableStringTemplate3StatementLocator("/com/ning/billing/commons/jdbi/SomethingNonLiteralSqlDao.sql.stg", true, true));
        final SomethingNonLiteralSqlDao somethingNonLiteralSqlDao = dbi.onDemand(SomethingNonLiteralSqlDao.class);

        somethingNonLiteralSqlDao.delete(TABLE_NAME);
        somethingNonLiteralSqlDao.delete(TABLE_NAME);
    }

    @Test(groups = "slow")
    public void testMultipleInvocationsWithLiterals() throws IOException {
        dbi.setStatementLocator(new ReusableStringTemplate3StatementLocator(SomethingLiteralSqlDao.class, true, true));
        final SomethingLiteralSqlDao somethingLiteralSqlDao = dbi.onDemand(SomethingLiteralSqlDao.class);

        somethingLiteralSqlDao.delete(TABLE_NAME);
        somethingLiteralSqlDao.delete(TABLE_NAME);
    }

    private static interface SomethingNonLiteralSqlDao extends Transactional<SomethingLiteralSqlDao> {

        @SqlUpdate
        public void delete(@Define("tableName") String table);
    }

    private static interface SomethingLiteralSqlDao extends Transactional<SomethingLiteralSqlDao> {

        @SqlUpdate("delete from <tableName>")
        public void delete(@Define("tableName") String table);
    }
}
