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

package org.killbill.commons.jdbi.template;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.ParameterizedType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.SqlStatementCustomizer;
import org.skife.jdbi.v2.sqlobject.SqlStatementCustomizingAnnotation;
import org.skife.jdbi.v2.sqlobject.stringtemplate.StringTemplate3StatementLocator;
import org.skife.jdbi.v2.sqlobject.stringtemplate.UseStringTemplate3StatementLocator;
import org.skife.jdbi.v2.tweak.StatementLocator;

@SqlStatementCustomizingAnnotation(KillBillSqlDaoStringTemplate.KillBillSqlDaoStringTemplateFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface KillBillSqlDaoStringTemplate {

    String DEFAULT_VALUE = " ~ ";

    String value() default DEFAULT_VALUE;

    class KillBillSqlDaoStringTemplateFactory extends UseStringTemplate3StatementLocator.LocatorFactory {

        static final boolean enableGroupTemplateCaching = Boolean.parseBoolean(System.getProperty("org.killbill.jdbi.allow.stringTemplateGroupCaching", "true"));

        static final ConcurrentMap<String, StatementLocator> locatorCache = new ConcurrentHashMap<String, StatementLocator>();

        //
        // This is only needed to compute the key for the cache -- whether we get a class or a pathname (string)
        //
        // (Similar to what jdbi is doing (StringTemplate3StatementLocator))
        //
        private static final String sep = "/"; // *Not* System.getProperty("file.separator"), which breaks in jars

        static String mungify(final Class claz) {
            final String path = "/" + claz.getName();
            return path.replaceAll("\\.", Matcher.quoteReplacement(sep)) + ".sql.stg";
        }

        private StatementLocator getLocator(final String locatorPath, final Class sqlObjectType) {

            if (enableGroupTemplateCaching && locatorCache.containsKey(locatorPath)) {
                return locatorCache.get(locatorPath);
            }

            final Class parent;
            if (sqlObjectType.getInterfaces().length > 0) {
                parent = sqlObjectType.getInterfaces()[0];
            } else {
                parent = sqlObjectType;
            }

            final Class superGroup;
            if (parent.getGenericSuperclass() instanceof ParameterizedType) {
                // A bit of java magic to extract parameterizedType
                final ParameterizedType parameterizedType = (ParameterizedType) parent.getGenericSuperclass();
                superGroup = (Class) parameterizedType.getActualTypeArguments()[0];
            } else {
                superGroup = parent;
            }

            final StringTemplate3StatementLocator.Builder builder = StringTemplate3StatementLocator.builder(locatorPath)
                                                                                                   .shouldCache()
                                                                                                   .withSuperGroup(superGroup)
                                                                                                   .allowImplicitTemplateGroup()
                                                                                                   .treatLiteralsAsTemplates();

            final StatementLocator locator = builder.build();
            if (enableGroupTemplateCaching) {
                locatorCache.put(locatorPath, locator);
            }
            return locator;
        }

        @Override
        public SqlStatementCustomizer createForType(final Annotation annotation, final Class sqlObjectType) {
            final KillBillSqlDaoStringTemplate a = (KillBillSqlDaoStringTemplate) annotation;

            final String locatorPath = DEFAULT_VALUE.equals(a.value()) ? mungify(sqlObjectType) : a.value();

            final StatementLocator l = getLocator(locatorPath, sqlObjectType);
            return new SqlStatementCustomizer() {
                @Override
                public void apply(final SQLStatement statement) {
                    statement.setStatementLocator(l);
                }
            };
        }
    }
}
