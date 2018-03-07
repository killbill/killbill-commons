/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.google.common.base.CaseFormat;

// Identical to org.skife.jdbi.v2.BeanMapper but maps created_date to createdDate
public class LowerToCamelBeanMapper<T> implements ResultSetMapper<T> {

    private final Class<T> type;
    private final Map<String, PropertyDescriptor> properties = new HashMap<String, PropertyDescriptor>();
    private final Map<String, PropertyMapper> propertiesMappers = new HashMap<String, PropertyMapper>();

    public LowerToCamelBeanMapper(final Class<T> type) {
        this.type = type;
        try {
            final BeanInfo info = Introspector.getBeanInfo(type);

            for (final PropertyDescriptor descriptor : info.getPropertyDescriptors()) {
                final String key = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, descriptor.getName()).toLowerCase();
                properties.put(key, descriptor);

                final PropertyMapper propertyMapper;
                final Class<?> propertyType = descriptor.getPropertyType();
                if (propertyType.isAssignableFrom(Boolean.class) || propertyType.isAssignableFrom(boolean.class)) {
                    propertyMapper = new PropertyMapper<ResultSet, Boolean>() {

                        @Override
                        public Boolean apply(final ResultSet rs, final int i) throws SQLException {
                            return rs.getBoolean(i);
                        }
                    };
                } else if (propertyType.isAssignableFrom(Byte.class) || propertyType.isAssignableFrom(byte.class)) {
                    propertyMapper = new PropertyMapper<ResultSet, Byte>() {

                        @Override
                        public Byte apply(final ResultSet rs, final int i) throws SQLException {
                            return rs.getByte(i);
                        }
                    };
                } else if (propertyType.isAssignableFrom(Short.class) || propertyType.isAssignableFrom(short.class)) {
                    propertyMapper = new PropertyMapper<ResultSet, Short>() {

                        @Override
                        public Short apply(final ResultSet rs, final int i) throws SQLException {
                            return rs.getShort(i);
                        }
                    };
                } else if (propertyType.isAssignableFrom(Integer.class) || propertyType.isAssignableFrom(int.class)) {
                    propertyMapper = new PropertyMapper<ResultSet, Integer>() {

                        @Override
                        public Integer apply(final ResultSet rs, final int i) throws SQLException {
                            return rs.getInt(i);
                        }
                    };
                } else if (propertyType.isAssignableFrom(Long.class) || propertyType.isAssignableFrom(long.class)) {
                    propertyMapper = new PropertyMapper<ResultSet, Long>() {

                        @Override
                        public Long apply(final ResultSet rs, final int i) throws SQLException {
                            return rs.getLong(i);
                        }
                    };
                } else if (propertyType.isAssignableFrom(Float.class) || propertyType.isAssignableFrom(float.class)) {
                    propertyMapper = new PropertyMapper<ResultSet, Float>() {

                        @Override
                        public Float apply(final ResultSet rs, final int i) throws SQLException {
                            return rs.getFloat(i);
                        }
                    };
                } else if (propertyType.isAssignableFrom(Double.class) || propertyType.isAssignableFrom(double.class)) {
                    propertyMapper = new PropertyMapper<ResultSet, Double>() {

                        @Override
                        public Double apply(final ResultSet rs, final int i) throws SQLException {
                            return rs.getDouble(i);
                        }
                    };
                } else if (propertyType.isAssignableFrom(BigDecimal.class)) {
                    propertyMapper = new PropertyMapper<ResultSet, BigDecimal>() {

                        @Override
                        public BigDecimal apply(final ResultSet rs, final int i) throws SQLException {
                            return rs.getBigDecimal(i);
                        }
                    };
                } else if (propertyType.isAssignableFrom(DateTime.class)) {
                    propertyMapper = new PropertyMapper<ResultSet, DateTime>() {

                        @Override
                        public DateTime apply(final ResultSet rs, final int i) throws SQLException {
                            final Timestamp timestamp = rs.getTimestamp(i);
                            return timestamp == null ? null : new DateTime(timestamp).toDateTime(DateTimeZone.UTC);
                        }
                    };
                } else if (propertyType.isAssignableFrom(Time.class)) {
                    propertyMapper = new PropertyMapper<ResultSet, Time>() {

                        @Override
                        public Time apply(final ResultSet rs, final int i) throws SQLException {
                            return rs.getTime(i);
                        }
                    };
                } else if (propertyType.isAssignableFrom(LocalDate.class)) {
                    propertyMapper = new PropertyMapper<ResultSet, LocalDate>() {

                        @Override
                        public LocalDate apply(final ResultSet rs, final int i) throws SQLException {
                            //
                            // We store the LocalDate into a mysql 'date' as a string
                            // (See https://github.com/killbill/killbill-commons/blob/master/jdbi/src/main/java/org/killbill/commons/jdbi/argument/LocalDateArgumentFactory.java)
                            // So we also read it as a String which avoids any kind of transformation
                            //
                            // Note that we used previously the getDate(index, Calendar) method, but this is not thread safe as we discovered
                            // unless maybe -- untested --we pass a new instance of a Calendar each time
                            //
                            final String dateString = rs.getString(i);
                            return dateString == null ? null : new LocalDate(dateString, DateTimeZone.UTC);
                        }
                    };
                } else if (propertyType.isAssignableFrom(DateTimeZone.class)) {
                    propertyMapper = new PropertyMapper<ResultSet, DateTimeZone>() {

                        @Override
                        public DateTimeZone apply(final ResultSet rs, final int i) throws SQLException {
                            final String dateTimeZoneString = rs.getString(i);
                            return dateTimeZoneString == null ? null : DateTimeZone.forID(dateTimeZoneString);
                        }
                    };
                } else if (propertyType.isAssignableFrom(String.class)) {
                    propertyMapper = new PropertyMapper<ResultSet, String>() {

                        @Override
                        public String apply(final ResultSet rs, final int i) throws SQLException {
                            return rs.getString(i);
                        }
                    };
                } else if (propertyType.isAssignableFrom(UUID.class)) {
                    propertyMapper = new PropertyMapper<ResultSet, UUID>() {

                        @Override
                        public UUID apply(final ResultSet rs, final int i) throws SQLException {
                            final String uuidString = rs.getString(i);
                            return uuidString == null ? null : UUID.fromString(uuidString);
                        }
                    };
                } else if (propertyType.isEnum()) {
                    propertyMapper = new PropertyMapper<ResultSet, Enum>() {

                        @Override
                        public Enum apply(final ResultSet rs, final int i) throws SQLException {
                            final String enumString = rs.getString(i);
                            return enumString == null ? null : Enum.valueOf((Class<Enum>) propertyType, enumString);
                        }
                    };
                } else if (propertyType == byte[].class) {
                    propertyMapper = new PropertyMapper<ResultSet, byte[]>() {

                        @Override
                        public byte[] apply(final ResultSet rs, final int i) throws SQLException {
                            return rs.getBytes(i);
                        }
                    };
                } else {
                    propertyMapper = new PropertyMapper<ResultSet, Time>() {

                        @Override
                        public Time apply(final ResultSet rs, final int i) throws SQLException {
                            return (Time) rs.getObject(i);
                        }
                    };
                }
                propertiesMappers.put(key, propertyMapper);
            }
        } catch (final IntrospectionException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Field getField(final Class clazz, final String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (final NoSuchFieldException e) {
            // Go up in the hierarchy
            final Class superClass = clazz.getSuperclass();
            if (superClass == null) {
                throw e;
            } else {
                return getField(superClass, fieldName);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public T map(final int row, final ResultSet rs, final StatementContext ctx) throws SQLException {
        final T bean;
        try {
            bean = type.newInstance();
        } catch (final Exception e) {
            throw new IllegalArgumentException(String.format("A bean, %s, was mapped " +
                                                             "which was not instantiable", type.getName()),
                                               e);
        }

        final Class beanClass = bean.getClass();
        final ResultSetMetaData metadata = rs.getMetaData();

        for (int i = 1; i <= metadata.getColumnCount(); ++i) {
            final String name = metadata.getColumnLabel(i).toLowerCase();

            final PropertyMapper propertyMapper = propertiesMappers.get(name);
            if (propertyMapper != null) {
                Object value = propertyMapper.apply(rs, i);

                // For h2, transform a JdbcBlob into a byte[]
                if (value instanceof Blob) {
                    final Blob blob = (Blob) value;
                    value = blob.getBytes(0, (int) blob.length());
                }
                if (rs.wasNull() && !type.isPrimitive()) {
                    value = null;
                }

                try {
                    final PropertyDescriptor descriptor = properties.get(name);
                    final Method writeMethod = descriptor.getWriteMethod();
                    if (writeMethod != null) {
                        writeMethod.invoke(bean, value);
                    } else {
                        final String camelCasedName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name);
                        final Field field = getField(beanClass, camelCasedName);
                        field.setAccessible(true); // Often private...
                        field.set(bean, value);
                    }
                } catch (final IllegalArgumentException e) {
                    throw new IllegalArgumentException(String.format("Unable to set field for " +
                                                                     "property: name=%s, value=%s", name, value), e);
                } catch (final NoSuchFieldException e) {
                    throw new IllegalArgumentException(String.format("Unable to find field for " +
                                                                     "property, %s", name), e);
                } catch (final IllegalAccessException e) {
                    throw new IllegalArgumentException(String.format("Unable to access setter for " +
                                                                     "property, %s", name), e);
                } catch (final InvocationTargetException e) {
                    throw new IllegalArgumentException(String.format("Invocation target exception trying to " +
                                                                     "invoker setter for the %s property", name), e);
                } catch (final NullPointerException e) {
                    throw new IllegalArgumentException(String.format("No appropriate method to " +
                                                                     "write value %s ", value), e);
                }
            }
        }

        return bean;
    }

    protected interface PropertyMapper<ResultSet, T> {

        T apply(ResultSet rs, int i) throws SQLException;
    }
}
