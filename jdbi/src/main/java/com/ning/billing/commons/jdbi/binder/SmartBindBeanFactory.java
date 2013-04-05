/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.commons.jdbi.binder;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;

// Similar to org.skife.jdbi.v2.sqlobject.BindBeanFactory but handles JodaTime, Mockito objects, etc.
public class SmartBindBeanFactory extends BinderBase implements BinderFactory {

    public Binder build(final Annotation annotation) {
        return new Binder<SmartBindBean, Object>() {
            public void bind(final SQLStatement q, final SmartBindBean bind, final Object arg) {
                final String prefix;
                if ("___jdbi_bare___".equals(bind.value())) {
                    prefix = "";
                } else {
                    prefix = bind.value() + ".";
                }

                try {
                    final BeanInfo infos = Introspector.getBeanInfo(arg.getClass());
                    final PropertyDescriptor[] props = infos.getPropertyDescriptors();
                    for (final PropertyDescriptor prop : props) {
                        final Method readMethod = prop.getReadMethod();
                        // Null for e.g. proxy objects (Mockito)
                        if (readMethod != null) {
                            final String bindingName = prefix + prop.getName();

                            final Object value = readMethod.invoke(arg);
                            if (value == null) {
                                // TODO right sql type?
                                q.bind(bindingName, (Object) null);
                            } else if (value instanceof DateTime) {
                                q.bind(bindingName, getDate((DateTime) value));
                            } else if (value instanceof DateTimeZone) {
                                q.bind(bindingName, value.toString());
                            } else if (value instanceof Enum /* Works for Enum inside classes */ || value.getClass().isEnum()) {
                                q.bind(bindingName, value.toString());
                            } else if (value instanceof LocalDate) {
                                // ISO8601 format
                                q.bind(bindingName, value.toString());
                            } else if (value instanceof UUID) {
                                q.bind(bindingName, getUUIDString((UUID) value));
                            } else {
                                q.bind(bindingName, value);
                            }
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("unable to bind bean properties", e);
                }
            }
        };
    }
}
