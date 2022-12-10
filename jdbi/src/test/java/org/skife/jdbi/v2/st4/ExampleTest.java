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

package org.skife.jdbi.v2.st4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.JDBITests;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;
import org.skife.jdbi.v2.sqlobject.helpers.MapResultAsBean;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ST4StatementLocator;
import org.skife.jdbi.v2.sqlobject.stringtemplate.UseST4StatementLocator;
import org.skife.jdbi.v2.tweak.HandleCallback;

import static org.assertj.core.api.Assertions.assertThat;

public class ExampleTest {

    @Rule
    public H2Rule h2 = new H2Rule();

    @Test
    @Category(JDBITests.class)
    public void testFluent() throws Exception {
        DBI dbi = new DBI(h2);
        dbi.setStatementLocator(ST4StatementLocator.fromClasspath("/org/skife/jdbi/v2/st4/ExampleTest.Dao.sql.stg"));

        dbi.withHandle(new HandleCallback<Object>() {
            @Override
            public Object withHandle(final Handle h) throws Exception {
                h.execute("createSomethingTable");

                int numCreated = h.createStatement("insertSomething")
                                  .bind("0", 0)
                                  .bind("1", "Jan")
                                  .execute();
                assertThat(numCreated).as("number of rows inserted").isEqualTo(1);

                String name = h.createQuery("findById")
                               .bind("0", 0)
                               .define("columns", "name")
                               .mapTo(String.class)
                               .first();
                assertThat(name).as("Jan's Name").isEqualTo("Jan");

                return null;
            }
        });
    }

    @UseST4StatementLocator
    public interface Dao {

        @SqlUpdate
        void createSomethingTable();

        @SqlUpdate
        int insertSomething(int id, String name);

        @SqlQuery
        @MapResultAsBean
        Something findById(int id, @Define("columns") String... columns);

        @SqlQuery("select concat('Hello, ', name, '!') from something where id = :0")
        String findGreetingFor(int id);
    }
}
