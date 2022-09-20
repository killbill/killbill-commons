/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.commons.jdbi.mapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.mockito.Mockito;
import org.skife.jdbi.v2.BeanMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestBeanMapper {

    private BeanMapper<Person> newBeanMapper() {
        final BeanMapper<Person> toSpy = new BeanMapper<>(Person.class);
        return Mockito.spy(toSpy);
    }

    private ResultSet createResultSet() throws SQLException {
        final ResultSetMetaData rsmd = Mockito.mock(ResultSetMetaData.class);
        Mockito.when(rsmd.getColumnCount()).thenReturn(3);
        Mockito.when(rsmd.getColumnLabel(1)).thenReturn("name");
        Mockito.when(rsmd.getColumnLabel(2)).thenReturn("age");
        Mockito.when(rsmd.getColumnLabel(3)).thenReturn("address"); // mocking result from db. Person doesn't have address attribute

        final ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getMetaData()).thenReturn(rsmd);
        Mockito.when(rs.getString(1)).thenReturn("some name");
        Mockito.when(rs.getInt(2)).thenReturn(30);
        Mockito.when(rs.getString(3)).thenReturn("some address"); // mocking db result

        return rs;
    }

    @Test(groups = "fast")
    public void testMap() throws SQLException {
        final BeanMapper<Person> spied = newBeanMapper();
        final ResultSet rs = createResultSet();

        final Person mapped = spied.map(0, rs, null);
        Assert.assertEquals(mapped.getName(), "some name");
        Assert.assertEquals(mapped.getAge(), 30);
    }


    public static class Person {

        private String name;
        private int age;

        public void setSomething() {}

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(final int age) {
            this.age = age;
        }
    }
}
