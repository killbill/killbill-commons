/*
 * Copyright 2010-2014 Ning, Inc.
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

package org.killbill.commons.jdbi.statement;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.SqlStatementCustomizer;
import org.skife.jdbi.v2.sqlobject.SqlStatementCustomizerFactory;
import org.skife.jdbi.v2.sqlobject.SqlStatementCustomizingAnnotation;
import org.skife.jdbi.v2.tweak.BaseStatementCustomizer;

// Similar to org.skife.jdbi.v2.sqlobject.customizers.FetchSize but a bit smarter:
// @FetchSize(Integer.MIN_VALUE) doesn't work for H2 for example.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER})
@SqlStatementCustomizingAnnotation(SmartFetchSize.Factory.class)
public @interface SmartFetchSize {

    int value() default 0;

    // Override value
    boolean shouldStream() default false;

    static class Factory implements SqlStatementCustomizerFactory {

        public SqlStatementCustomizer createForMethod(final Annotation annotation, final Class sqlObjectType, final Method method) {
            final SmartFetchSize fs = (SmartFetchSize) annotation;
            return new SqlStatementCustomizer() {
                public void apply(final SQLStatement q) throws SQLException {
                    setFetchSize((Query) q, fs.value(), fs.shouldStream());
                }
            };
        }

        public SqlStatementCustomizer createForType(final Annotation annotation, final Class sqlObjectType) {
            final SmartFetchSize fs = (SmartFetchSize) annotation;
            return new SqlStatementCustomizer() {
                public void apply(final SQLStatement q) throws SQLException {
                    setFetchSize((Query) q, fs.value(), fs.shouldStream());
                }
            };
        }

        public SqlStatementCustomizer createForParameter(final Annotation annotation, final Class sqlObjectType, final Method method, final Object arg) {
            final Integer va = (Integer) arg;
            return new SqlStatementCustomizer() {
                public void apply(final SQLStatement q) throws SQLException {
                    setFetchSize((Query) q, va, false);
                }
            };
        }

        private Query setFetchSize(final Query query, final Integer value, final boolean shouldStream) {
            query.addStatementCustomizer(new SmartFetchSizeCustomizer(value, shouldStream));
            return query;
        }
    }

    public static final class SmartFetchSizeCustomizer extends BaseStatementCustomizer {

        // Shared name across drivers, see org.mariadb.jdbc.MySQLDatabaseMetaData and com.mysql.jdbc.DatabaseMetaData
        private static final String MYSQL = "MySQL";

        private final int fetchSize;
        private final boolean shouldStream;

        public SmartFetchSizeCustomizer(final int fetchSize, final boolean shouldStream) {
            this.fetchSize = fetchSize;
            this.shouldStream = shouldStream;
        }

        @Override
        public void beforeExecution(final PreparedStatement stmt, final StatementContext ctx) throws SQLException {
            stmt.setFetchSize(fetchSize);
            if (shouldStream) {
                if (ctx != null &&
                    ctx.getConnection() != null &&
                    ctx.getConnection().getMetaData() != null &&
                    MYSQL.equalsIgnoreCase(ctx.getConnection().getMetaData().getDatabaseProductName())) {
                    try {
                        // Magic value to force MySQL to stream from the database
                        // See http://dev.mysql.com/doc/refman/5.0/en/connector-j-reference-implementation-notes.html (ResultSet)
                        stmt.setFetchSize(Integer.MIN_VALUE);
                    } catch (final SQLException e) {
                        // Shouldn't happen? The exception will be logged by log4jdbc
                        stmt.setFetchSize(0);
                    }
                } else {
                    // Other engines (H2, PostgreSQL, etc.)
                    stmt.setFetchSize(0);
                }
            } else {
                stmt.setFetchSize(fetchSize);
            }
        }
    }
}
