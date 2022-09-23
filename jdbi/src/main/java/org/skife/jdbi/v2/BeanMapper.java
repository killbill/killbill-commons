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

import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * A result set mapper which maps the fields in a statement into a JavaBean. This uses
 * the JDK's built-in bean mapping facilities, so it does not support nested properties.
 */
public class BeanMapper<T> implements ResultSetMapper<T> {
    private final Class<T> type;
    private final Map<String, PropertyDescriptor> properties = new HashMap<>();

    public BeanMapper(final Class<T> type) {
        this.type = type;
        try {
            final BeanInfo info = Introspector.getBeanInfo(type);

            for (final PropertyDescriptor descriptor : info.getPropertyDescriptors()) {
                properties.put(descriptor.getName().toLowerCase(), descriptor);
            }
        }
        catch (final IntrospectionException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    @SuppressWarnings({"rawtypes"})
    public T map(final int row, final ResultSet rs, final StatementContext ctx) throws SQLException {
        final T bean;
        try {
            bean = type.getDeclaredConstructor().newInstance();
        } catch (final Exception e) {
            throw new IllegalArgumentException(String.format("A bean, %s, was mapped which was not instantiable", type.getName()), e);
        }

        final ResultSetMetaData metadata = rs.getMetaData();

        for (int i = 1; i <= metadata.getColumnCount(); ++i) {
            final String name = metadata.getColumnLabel(i).toLowerCase();
            final PropertyDescriptor descriptor = properties.get(name);

            if (descriptor != null) {
                final Class type = descriptor.getPropertyType();
                final Object value = getValueFromResultSet(rs, type, i);
                final Method method = descriptor.getWriteMethod();
                if (method == null) {
                    throw new IllegalArgumentException(String.format("No appropriate method to write property %s", name));
                }
                try {
                    descriptor.getWriteMethod().invoke(bean, value);
                }
                catch (final IllegalAccessException e) {
                    throw new IllegalArgumentException(String.format("Unable to access setter for property, %s", name), e);
                } catch (final InvocationTargetException e) {
                    throw new IllegalArgumentException(String.format("Invocation target exception trying to invoker setter for the %s property", name), e);
                }
            }
        }
        return bean;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @VisibleForTesting
    Object getValueFromResultSet(final ResultSet rs, final Class type, final int index) throws SQLException {
        final Object result;

        if (type.isAssignableFrom(Boolean.class) || type.isAssignableFrom(boolean.class)) {
            result = rs.getBoolean(index);
        }
        else if (type.isAssignableFrom(Byte.class) || type.isAssignableFrom(byte.class)) {
            result = rs.getByte(index);
        }
        else if (type.isAssignableFrom(Short.class) || type.isAssignableFrom(short.class)) {
            result = rs.getShort(index);
        }
        else if (type.isAssignableFrom(Integer.class) || type.isAssignableFrom(int.class)) {
            result = rs.getInt(index);
        }
        else if (type.isAssignableFrom(Long.class) || type.isAssignableFrom(long.class)) {
            result = rs.getLong(index);
        }
        else if (type.isAssignableFrom(Float.class) || type.isAssignableFrom(float.class)) {
            result = rs.getFloat(index);
        }
        else if (type.isAssignableFrom(Double.class) || type.isAssignableFrom(double.class)) {
            result = rs.getDouble(index);
        }
        else if (type.isAssignableFrom(BigDecimal.class)) {
            result = rs.getBigDecimal(index);
        }
        else if (type.isAssignableFrom(Timestamp.class)) {
            result = rs.getTimestamp(index);
        }
        else if (type.isAssignableFrom(Time.class)) {
            result = rs.getTime(index);
        }
        else if (type.isAssignableFrom(Date.class)) {
            result = rs.getDate(index);
        }
        else if (type.isAssignableFrom(String.class)) {
            result = rs.getString(index);
        }
        else if (type.isEnum()) {
            final String str = rs.getString(index);
            result = str != null ? Enum.valueOf(type, str) : null;
        }
        else {
            result = rs.getObject(index);
        }

        return (rs.wasNull() && !type.isPrimitive()) ? null : result;
    }
}

