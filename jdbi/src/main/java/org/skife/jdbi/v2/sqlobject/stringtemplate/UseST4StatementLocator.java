/*
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

package org.skife.jdbi.v2.sqlobject.stringtemplate;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.sql.SQLException;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.SqlStatementCustomizer;
import org.skife.jdbi.v2.sqlobject.SqlStatementCustomizerFactory;
import org.skife.jdbi.v2.sqlobject.SqlStatementCustomizingAnnotation;
import org.skife.jdbi.v2.tweak.StatementLocator;

/**
 * Use StringTemplate 4 to load a stringtemplate group containing named templates for each sql statement.
 * <p>
 * STGroup is loaded from the classpath via sqlObjectType.getResource( ), such that names line
 * up with the package and class, so: com.example.Foo will look for /com/example/Foo.sql.stg . Inner classes
 * are separated in the file name by a '.' not a '$', so com.example.Foo.Bar (Bar is an inner class of Foo) would
 * be at /com/example/Foo.Bar.sql.stg .
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@SqlStatementCustomizingAnnotation(UseST4StatementLocator.LocatorFactory.class)
public @interface UseST4StatementLocator {

    String USE_SQLOBJECT_TYPE_INDICATOR = "$$# ^&&*% $$!$$";

    /**
     * Path on classpath to load group file from, if not using automagic naming conventions
     * If you want to use automagic naming conventions, don't set this value.
     *
     * @return path on classpath to load stringtemplate group file from, ie "/sql/all_the_sql.stg"
     */
    String groupFile() default USE_SQLOBJECT_TYPE_INDICATOR;

    /**
     * Set to false if for some reason you don't want to cache STGroups. It is very unusual to NOT cache them, so if
     * you are not sure what to do, leave this alone.
     */
    boolean useTemplateGroupCache() default true;

    class LocatorFactory implements SqlStatementCustomizerFactory {

        private static SqlStatementCustomizer make(final UseST4StatementLocator an, final Class sqlObjectType) {
            final StatementLocator locator;
            if (USE_SQLOBJECT_TYPE_INDICATOR.equals(an.groupFile())) {
                locator = ST4StatementLocator.forType(sqlObjectType);
            } else {
                final ST4StatementLocator.UseSTGroupCache useCache = an.useTemplateGroupCache()
                                                                     ? ST4StatementLocator.UseSTGroupCache.YES
                                                                     : ST4StatementLocator.UseSTGroupCache.NO;

                locator = ST4StatementLocator.fromClasspath(useCache,
                                                            an.groupFile());
            }

            return new SqlStatementCustomizer() {
                @Override
                public void apply(final SQLStatement q) throws SQLException {
                    q.setStatementLocator(locator);
                }
            };
        }

        @Override
        public SqlStatementCustomizer createForMethod(final Annotation annotation,
                                                      final Class sqlObjectType,
                                                      final Method method) {
            return make((UseST4StatementLocator) annotation, sqlObjectType);
        }

        @Override
        public SqlStatementCustomizer createForType(final Annotation annotation, final Class sqlObjectType) {
            return make((UseST4StatementLocator) annotation, sqlObjectType);
        }

        @Override
        public SqlStatementCustomizer createForParameter(final Annotation annotation,
                                                         final Class sqlObjectType,
                                                         final Method method,
                                                         final Object arg) {
            throw new UnsupportedOperationException("Annotation cannot be applied to parameter");
        }
    }
}
