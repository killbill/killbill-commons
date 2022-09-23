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


package org.skife.jdbi.v2;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestBeanMapper {

    private <T> BeanMapper<T> newBeanMapper(final Class<T> beanClass) {
        final BeanMapper<T> toSpy = new BeanMapper<>(beanClass);
        return Mockito.spy(toSpy);
    }

    private ResultSet createResultSet() throws SQLException {
        final ResultSetMetaData rsmd = Mockito.mock(ResultSetMetaData.class);
        Mockito.when(rsmd.getColumnCount()).thenReturn(3);
        Mockito.when(rsmd.getColumnLabel(1)).thenReturn("name");
        Mockito.when(rsmd.getColumnLabel(2)).thenReturn("age");
        Mockito.when(rsmd.getColumnLabel(3)).thenReturn("address"); // mocking result from db, make sure that db operation considered as valid operation

        final ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getMetaData()).thenReturn(rsmd);
        Mockito.when(rs.getString(1)).thenReturn("some name");
        Mockito.when(rs.getInt(2)).thenReturn(30);
        Mockito.when(rs.getString(3)).thenReturn("some address"); // make sure that db operation considered as valid operation

        return rs;
    }

    @Test(groups = "fast")
    public void testMapWithClassWithSetter() throws SQLException {
        final BeanMapper<PersonWithSetter> spied = newBeanMapper(PersonWithSetter.class);
        final ResultSet rs = createResultSet();

        final PersonWithSetter mapped = spied.map(0, rs, null);
        Assert.assertEquals(mapped.getName(), "some name");
        Assert.assertEquals(mapped.getAge(), 30);
    }

    @Test(groups = "fast")
    public void testMapWithClassWithoutSetter() throws SQLException {
        final BeanMapper<PersonWithoutSetter> spied = newBeanMapper(PersonWithoutSetter.class);
        final ResultSet rs = createResultSet();


        try {
            spied.map(0, rs, null);
            Assert.fail("PersonWithoutSetter contains no setter method, and should fails");
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("No appropriate method to write property"));
        }
    }

    static class PersonWithSetter {

        private String name;
        private int age;

        public String getName() { return name; }

        public void setName(final String name) { this.name = name; }

        public int getAge() { return age; }

        public void setAge(final int age) { this.age = age; }
    }

    static class PersonWithoutSetter { // No setter for address

        private String name;
        private int age;
        private String address;

        public String getName() { return name; }

        public void setName(final String name) { this.name = name; }

        public int getAge() { return age; }

        public void setAge(final int age) { this.age = age; }

        public String getAddress() { return address; }

        public void doSomething(final String address) { this.address = address; }
    }
}