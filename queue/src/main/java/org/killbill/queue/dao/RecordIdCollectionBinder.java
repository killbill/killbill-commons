/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.queue.dao;


import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

@BindingAnnotation(RecordIdCollectionBinder.RecordIdCollectionBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface RecordIdCollectionBinder {

    public static class RecordIdCollectionBinderFactory implements BinderFactory {

        @Override
        public Binder build(Annotation annotation) {
            return new Binder<RecordIdCollectionBinder, Collection<Long>>() {

                @Override
                public void bind(SQLStatement<?> query, RecordIdCollectionBinder bind, Collection<Long> ids) {
                    query.define("record_ids", ids);

                    int idx = 0;
                    for (Long id : ids) {
                        query.bind("id_" + idx, id);
                        idx++;
                    }
                }
            };
        }
    }
}
