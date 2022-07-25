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
package org.skife.jdbi.v2.sqlobject;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.JDBITests;
import org.skife.jdbi.v2.Something;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.helpers.MapResultAsBean;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestRegisteredMappersWork
{
    private DBI    dbi;
    private Handle handle;

    @Before
    public void setUp() throws Exception
    {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        dbi = new DBI(ds);
        dbi.registerMapper(new MySomethingMapper());
        handle = dbi.open();

        handle.execute("create table something (id int primary key, name varchar(100))");
    }

    @After
    public void tearDown() throws Exception
    {
        handle.execute("drop table something");
        handle.close();
    }


    public static interface BooleanDao {
        @SqlQuery("select 1+1 = 2")
        public boolean fetchABoolean();
    }

    @Test
    @Category(JDBITests.class)
    public void testFoo() throws Exception
    {
        boolean world_is_right = handle.attach(BooleanDao.class).fetchABoolean();
        assertThat(world_is_right, equalTo(true));
    }

    public static class Bean
    {
        private String name;
        private String color;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getColor()
        {
            return color;
        }

        public void setColor(String color)
        {
            this.color = color;
        }
    }

    public static interface BeanMappingDao
    {
        @SqlUpdate("create table beans ( name varchar primary key, color varchar )")
        public void createBeanTable();

        @SqlUpdate("insert into beans (name, color) values (:name, :color)")
        public void insertBean(@BindBean Bean bean);

        @SqlQuery("select name, color from beans where name = :name")
        @MapResultAsBean
        public Bean findByName(@Bind("name") String name);
    }

    @Test
    @Category(JDBITests.class)
    public void testBuiltIn() throws Exception
    {

        Spiffy s = SqlObjectBuilder.attach(handle, Spiffy.class);

        s.insert(1, "Tatu");

        assertEquals("Tatu", s.findNameBy(1));
    }

    @Test
    @Category(JDBITests.class)
    public void testRegisterMapperAnnotationWorks() throws Exception
    {
        Kabob bob = dbi.onDemand(Kabob.class);

        bob.insert(1, "Henning");
        Something henning = bob.find(1);

        assertThat(henning, equalTo(new Something(1, "Henning")));
    }

    @Test
    @Category(JDBITests.class)
    public void testNoErrorOnNoData() throws Exception
    {
        Kabob bob = dbi.onDemand(Kabob.class);

        Something henning = bob.find(1);
        assertThat(henning, nullValue());

        List<Something> rs = bob.listAll();
        assertThat(rs.isEmpty(), equalTo(true));

        Iterator<Something> itty = bob.iterateAll();
        assertThat(itty.hasNext(), equalTo(false));
    }

    public static interface Spiffy extends CloseMe
    {

        @SqlQuery("select id, name from something where id = :id")
        public Something byId(@Bind("id") long id);

        @SqlQuery("select name from something where id = :id")
        public String findNameBy(@Bind("id") long id);

        @SqlUpdate("insert into something (id, name) values (:id, :name)")
        public void insert(@Bind("id") long id, @Bind("name") String name);
    }


    public static interface Kabob
    {
        @SqlUpdate("insert into something (id, name) values (:id, :name)")
        public void insert(@Bind("id") int id, @Bind("name") String name);

        @SqlQuery("select id, name from something where id = :id")
        public Something find(@Bind("id") int id);

        @SqlQuery("select id, name from something order by id")
        public List<Something> listAll();

        @SqlQuery("select id, name from something order by id")
        public Iterator<Something> iterateAll();
    }

    public static class MySomethingMapper implements ResultSetMapper<Something>
    {
        @Override
        public Something map(int index, ResultSet r, StatementContext ctx) throws SQLException
        {
            return new Something(r.getInt("id"), r.getString("name"));
        }
    }

}
