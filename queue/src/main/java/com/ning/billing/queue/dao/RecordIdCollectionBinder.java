package com.ning.billing.queue.dao;


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
