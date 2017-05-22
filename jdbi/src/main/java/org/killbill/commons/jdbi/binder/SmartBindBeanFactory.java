/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.commons.jdbi.binder;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;

// Similar to org.skife.jdbi.v2.sqlobject.BindBeanFactory with optimizations
public class SmartBindBeanFactory implements BinderFactory {

    private static final Binder<SmartBindBean, Object> SMART_BINDER = new Binder<SmartBindBean, Object>() {
        @Override
        public void bind(final SQLStatement q, final SmartBindBean bind, final Object arg) {
            final String prefix;
            if (BindBean.BARE_BINDING.equals(bind.value())) {
                prefix = "";
            } else {
                prefix = bind.value() + ".";
            }

            try {
                final BeanInfo infos = Introspector.getBeanInfo(arg.getClass());
                final PropertyDescriptor[] props = infos.getPropertyDescriptors();
                for (final PropertyDescriptor prop : props) {
                    final Method readMethod = prop.getReadMethod();
                    if (readMethod != null) {
                        q.dynamicBind(readMethod.getReturnType(), prefix + prop.getName(), readMethod.invoke(arg));
                    }
                }
            } catch (final Exception e) {
                throw new IllegalStateException("unable to bind bean properties", e);
            }
        }
    };

    @Override
    public Binder build(final Annotation annotation) {
        return SMART_BINDER;
    }
}
