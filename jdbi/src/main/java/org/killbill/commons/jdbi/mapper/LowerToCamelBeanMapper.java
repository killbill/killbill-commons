/*
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
import org.killbill.commons.utils.Strings;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

// Identical to org.skife.jdbi.v2.BeanMapper but maps created_date to createdDate
public class LowerToCamelBeanMapper<T> implements ResultSetMapper<T> {

    private final Class<T> type;
    private final Map<String, PropertyDescriptor> properties = new HashMap<>();
    private final Map<String, PropertyMapper<ResultSet, ?>> propertiesMappers = new HashMap<>();

    public LowerToCamelBeanMapper(final Class<T> type) {
        this.type = type;
        try {
            final BeanInfo info = Introspector.getBeanInfo(type);

            for (final PropertyDescriptor descriptor : info.getPropertyDescriptors()) {
                final String key = Strings.toSnakeCase(descriptor.getName()).toLowerCase();
                properties.put(key, descriptor);

                final PropertyMapper<ResultSet, ?> propertyMapper = getPropertyMapper(descriptor);
                propertiesMappers.put(key, propertyMapper);
            }
        } catch (final IntrospectionException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static PropertyMapper<ResultSet, ?> getPropertyMapper(final PropertyDescriptor descriptor) {
        final PropertyMapper<ResultSet, ?> propertyMapper;
        final Class<?> propertyType = descriptor.getPropertyType();

        if (propertyType.isAssignableFrom(Boolean.class) || propertyType.isAssignableFrom(boolean.class)) {
            propertyMapper = ResultSet::getBoolean;

        } else if (propertyType.isAssignableFrom(Byte.class) || propertyType.isAssignableFrom(byte.class)) {
            propertyMapper = ResultSet::getByte;

        } else if (propertyType.isAssignableFrom(Short.class) || propertyType.isAssignableFrom(short.class)) {
            propertyMapper = ResultSet::getShort;

        } else if (propertyType.isAssignableFrom(Integer.class) || propertyType.isAssignableFrom(int.class)) {
            propertyMapper = ResultSet::getInt;

        } else if (propertyType.isAssignableFrom(Long.class) || propertyType.isAssignableFrom(long.class)) {
            propertyMapper = ResultSet::getLong;

        } else if (propertyType.isAssignableFrom(Float.class) || propertyType.isAssignableFrom(float.class)) {
            propertyMapper = ResultSet::getFloat;

        } else if (propertyType.isAssignableFrom(Double.class) || propertyType.isAssignableFrom(double.class)) {
            propertyMapper = ResultSet::getDouble;

        } else if (propertyType.isAssignableFrom(BigDecimal.class)) {
            propertyMapper = ResultSet::getBigDecimal;

        } else if (propertyType.isAssignableFrom(DateTime.class)) {
            propertyMapper = (rs, i) -> {
                final Timestamp timestamp = rs.getTimestamp(i);
                return timestamp == null ? null : new DateTime(timestamp).toDateTime(DateTimeZone.UTC);
            };

        } else if (propertyType.isAssignableFrom(Time.class)) {
            propertyMapper = ResultSet::getTime;

        } else if (propertyType.isAssignableFrom(LocalDate.class)) {
            propertyMapper = (rs, i) -> {
                // We store the LocalDate into a mysql 'date' as a string
                // (See https://github.com/killbill/killbill-commons/blob/master/jdbi/src/main/java/org/killbill/commons/jdbi/argument/LocalDateArgumentFactory.java)
                // So we also read it as a String which avoids any kind of transformation
                //
                // Note that we used previously the getDate(index, Calendar) method, but this is not thread safe as we discovered
                // unless maybe -- untested --we pass a new instance of a Calendar each time
                //
                final String dateString = rs.getString(i);
                return dateString == null ? null : new LocalDate(dateString, DateTimeZone.UTC);
            };

        } else if (propertyType.isAssignableFrom(DateTimeZone.class)) {
            propertyMapper = (rs, i) -> {
                final String dateTimeZoneString = rs.getString(i);
                return dateTimeZoneString == null ? null : DateTimeZone.forID(dateTimeZoneString);
            };

        } else if (propertyType.isAssignableFrom(String.class)) {
            propertyMapper = ResultSet::getString;

        } else if (propertyType.isAssignableFrom(UUID.class)) {
            propertyMapper = (rs, i) -> {
                final String uuidString = rs.getString(i);
                return uuidString == null ? null : UUID.fromString(uuidString);
            };

        } else if (propertyType.isEnum()) {
            propertyMapper = (rs, i) -> {
                final String enumString = rs.getString(i);
                return enumString == null ? null : Enum.valueOf((Class<Enum>) propertyType, enumString);
            };

        } else if (propertyType == byte[].class) {
            propertyMapper = ResultSet::getBytes;

        } else {
            propertyMapper = (rs, i) -> (Time) rs.getObject(i);
        }

        return propertyMapper;
    }

    private static Field getField(final Class<?> clazz, final String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (final NoSuchFieldException e) {
            // Go up in the hierarchy
            final Class<?> superClass = clazz.getSuperclass();
            if (superClass == null) {
                throw e;
            } else {
                return getField(superClass, fieldName);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    public T map(final int row, final ResultSet rs, final StatementContext ctx) throws SQLException {
        final T bean;
        try {
            bean = type.getDeclaredConstructor().newInstance();
        } catch (final Exception e) {
            throw new IllegalArgumentException(String.format("A bean, %s, was mapped which was not instantiable", type.getName()), e);
        }

        final Class beanClass = bean.getClass();
        final ResultSetMetaData metadata = rs.getMetaData();

        for (int i = 1; i <= metadata.getColumnCount(); ++i) {
            final String name = metadata.getColumnLabel(i).toLowerCase();

            final PropertyMapper<ResultSet, ?> propertyMapper = propertiesMappers.get(name);
            if (propertyMapper != null) {
                Object value = propertyMapper.apply(rs, i);

                // For h2, transform a JdbcBlob into a byte[]
                if (value instanceof Blob) {
                    final Blob blob = (Blob) value;
                    value = blob.getBytes(1, (int) blob.length());
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
                        final String camelCasedName = Strings.toCamelCase(name, false, '_');
                        final Field field = getField(beanClass, camelCasedName);
                        field.setAccessible(true); // Often private...
                        field.set(bean, value);
                    }
                } catch (final IllegalArgumentException e) {
                    throw new IllegalArgumentException(String.format("Unable to set field for property: name=%s, value=%s", name, value), e);
                } catch (final NoSuchFieldException e) {
                    throw new IllegalArgumentException(String.format("Unable to find field for property, %s", name), e);
                } catch (final IllegalAccessException e) {
                    throw new IllegalArgumentException(String.format("Unable to access setter for property, %s", name), e);
                } catch (final InvocationTargetException e) {
                    throw new IllegalArgumentException(String.format("Invocation target exception trying to invoker setter for the %s property", name), e);
                }
            }
        }

        return bean;
    }

    protected interface PropertyMapper<ResultSet, T> {

        T apply(ResultSet rs, int i) throws SQLException;
    }
}
