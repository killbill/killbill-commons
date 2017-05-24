/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.skife.jdbi.v2.sqlobject.stringtemplate;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.skife.jdbi.v2.JDBITests;
import org.stringtemplate.v4.STGroupFile;

import com.google.common.collect.ImmutableMap;

@Category(JDBITests.class)
public class TestST4StatementLocator {

    @Test
    public void testIds() throws Exception {
        final ST4StatementLocator statementLocator = new ST4StatementLocator(new STGroupFile("org/skife/jdbi/v2/sqlobject/stringtemplate/Jun.sql.stg"));
        Assert.assertEquals(0, statementLocator.locatedSqlCache.size());

        Assert.assertEquals("select * from foo where id in ()", statementLocator.locate("get", new TestingStatementContext(ImmutableMap.<String, Object>of())));
        Assert.assertEquals(1, statementLocator.locatedSqlCache.size());
        Assert.assertEquals("select * from foo where id in ()", statementLocator.locatedSqlCache.get("get"));

        // See @BindIn
        Assert.assertEquals("select * from foo where id in (:__ids_0)", statementLocator.locate("get", new TestingStatementContext(ImmutableMap.<String, Object>of("ids", ":__ids_0"))));
        Assert.assertEquals(2, statementLocator.locatedSqlCache.size());
        Assert.assertEquals("select * from foo where id in (:__ids_0)", statementLocator.locatedSqlCache.get("get___#___ids___#___:__ids_0"));

        Assert.assertEquals("select * from foo where id in (:__ids_0,:__ids_1)", statementLocator.locate("get", new TestingStatementContext(ImmutableMap.<String, Object>of("ids", ":__ids_0,:__ids_1"))));
        Assert.assertEquals(3, statementLocator.locatedSqlCache.size());
        Assert.assertEquals("select * from foo where id in (:__ids_0,:__ids_1)", statementLocator.locatedSqlCache.get("get___#___ids___#___:__ids_0,:__ids_1"));
    }
}
