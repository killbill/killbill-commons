/*
 * Copyright 2004-2014 Brian McCallister
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
package org.skife.jdbi.v2;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.ArgumentFactory;
import org.skife.jdbi.v2.util.StringMapper;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestArgumentFactory
{
    private DBI    dbi;
    private Handle h;

    @Before
    public void setUp() throws Exception
    {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        dbi = new DBI(ds);
        h = dbi.open();

        h.execute("create table something (id int primary key, name varchar(100))");

    }

    @After
    public void tearDown() throws Exception
    {
        h.execute("drop table something");
        h.close();
    }

    @Test
    @Category(JDBITests.class)
    public void testRegisterOnDBI() throws Exception
    {
        dbi.registerArgumentFactory(new NameAF());
        Handle h2 = dbi.open();
        h2.createStatement("insert into something (id, name) values (:id, :name)")
          .bind("id", 7)
          .bind("name", new Name("Brian", "McCallister"))
          .execute();

        String full_name = h.createQuery("select name from something where id = 7").map(StringMapper.FIRST).first();

        assertThat(full_name, equalTo("Brian McCallister"));
        h2.close();
    }

    public static class NameAF implements ArgumentFactory<Name>
    {
        @Override
        public boolean accepts(Class<?> expectedType, Object value, StatementContext ctx)
        {
            return expectedType == Object.class && value instanceof Name;
        }

        @Override
        public Argument build(Class<?> expectedType, Name value, StatementContext ctx)
        {
            return new StringArgument(value.getFullName());
        }
    }

    public static class Name
    {
        private final String first;
        private final String last;

        public Name(String first, String last)
        {
            this.first = first;
            this.last = last;
        }

        public String getFullName()
        {
            return first + " " + last;
        }

        @Override
        public String toString()
        {
            return "<Name first=" + first + " last=" + last + " >";
        }
    }

}
