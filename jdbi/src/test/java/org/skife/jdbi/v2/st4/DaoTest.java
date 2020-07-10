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
import org.skife.jdbi.v2.JDBIQuarantineTests;
import org.skife.jdbi.v2.JDBITests;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;
import org.skife.jdbi.v2.sqlobject.helpers.MapResultAsBean;
import org.skife.jdbi.v2.sqlobject.stringtemplate.UseST4StatementLocator;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.util.StringMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class DaoTest {

    @Rule
    public final H2Rule h2 = new H2Rule();

    @Test
    @Category(JDBITests.class)
    public void testSimpleStatement() throws Exception {
        final DBI dbi = new DBI(h2);
        final OuterDao dao = dbi.onDemand(OuterDao.class);
        dao.createSomething2();
        dbi.withHandle(new HandleCallback<Object>() {
            @Override
            public Object withHandle(final Handle h) throws Exception {
                h.execute("insert into something (id, name) values (1, 'Kyle')");
                return null;
            }
        });
    }

    @Test
    @Category(JDBITests.class)
    public void testDefineSomething() throws Exception {
        final DBI dbi = new DBI(h2);
        final OuterDao dao = dbi.onDemand(OuterDao.class);
        dao.createSomething2();
        dao.insert2("something", 1, "Carlos");

        final String name = dbi.withHandle(new HandleCallback<String>() {
            @Override
            public String withHandle(final Handle h) throws Exception {
                return h.createQuery("select name from something where id = 1")
                        .map(new StringMapper())
                        .first();
            }
        });
        assertThat(name).isEqualTo("Carlos");
    }

    @Test
    @Category(JDBIQuarantineTests.class)
    public void testUseImportedTemplate() throws Exception {
        final DBI dbi = new DBI(h2);
        final OuterDao dao = dbi.onDemand(OuterDao.class);
        dao.createSomething2();
        dao.insert2("something", 1, "Paul");
        final Something s = dao.findById2(1);

        assertThat(s).isEqualTo(new Something(1, "Paul"));
    }

    @Test
    @Category(JDBITests.class)
    public void testSimpleStatementInnerClass() throws Exception {
        final DBI dbi = new DBI(h2);
        final InnerDao dao = dbi.onDemand(InnerDao.class);
        dao.createSomething();
        dbi.withHandle(new HandleCallback<Object>() {
            @Override
            public Object withHandle(final Handle h) throws Exception {
                h.execute("insert into something (id, name) values (1, 'Kyle')");
                return null;
            }
        });
    }

    @Test
    @Category(JDBITests.class)
    public void testDefineSomethingInnerClass() throws Exception {
        final DBI dbi = new DBI(h2);
        final InnerDao dao = dbi.onDemand(InnerDao.class);
        dao.createSomething();
        dao.insert("something", 1, "Carlos");

        final String name = dbi.withHandle(new HandleCallback<String>() {
            @Override
            public String withHandle(final Handle h) throws Exception {
                return h.createQuery("select name from something where id = 1")
                        .map(new StringMapper())
                        .first();
            }
        });
        assertThat(name).isEqualTo("Carlos");
    }

    @Test
    @Category(JDBIQuarantineTests.class)
    public void testUseImportedTemplateInnerClass() throws Exception {
        final DBI dbi = new DBI(h2);
        final InnerDao dao = dbi.onDemand(InnerDao.class);
        dao.createSomething();
        dao.insert("something", 1, "Paul");
        final Something s = dao.findById(1);

        assertThat(s).isEqualTo(new Something(1, "Paul"));

    }

    @Test
    @Category(JDBITests.class)
    public void testSqlLiteral() throws Exception {
        final DBI dbi = new DBI(h2);
        final InnerDao dao = dbi.onDemand(InnerDao.class);
        dao.createSomething();
        dao.insert("something", 1, "Kyle");
        final String name = dao.findNameById(1);
        assertThat(name).isEqualTo("Kyle");
    }

    @Test
    @Category(JDBITests.class)
    public void testLocatorOnMethod() throws Exception {
        final DBI dbi = new DBI(h2);
        final InnerDao dao = dbi.onDemand(InnerDao.class);
        final OnMethod om = dbi.onDemand(OnMethod.class);

        dao.createSomething();
        om.insertVivek();

        final String name = dao.findNameById(3);
        assertThat(name).isEqualTo("Vivek");
    }

    public interface OnMethod {

        @SqlUpdate
        @UseST4StatementLocator
        public void insertVivek();
    }

    @UseST4StatementLocator
    public interface InnerDao {

        @SqlUpdate
        void createSomething();

        @SqlUpdate
        void insert(@Define("table") String table, @Bind("id") int id, @Bind("name") String name);

        @SqlQuery
        @MapResultAsBean
        Something findById(@Bind("id") int id);

        @SqlQuery("select name from something where id = :id")
        String findNameById(@Bind("id") int id);
    }

}
