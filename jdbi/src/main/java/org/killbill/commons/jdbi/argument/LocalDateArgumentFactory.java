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

package org.killbill.commons.jdbi.argument;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import org.joda.time.LocalDate;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.ArgumentFactory;

public class LocalDateArgumentFactory implements ArgumentFactory<LocalDate> {

    @Override
    public boolean accepts(final Class<?> expectedType, final Object value, final StatementContext ctx) {
        return value instanceof LocalDate;
    }

    @Override
    public Argument build(final Class<?> expectedType, final LocalDate value, final StatementContext ctx) {
        return new LocalDateArgument(value);
    }

    public static class LocalDateArgument implements Argument {

        // See org.postgresql.jdbc2.AbstractJdbc2DatabaseMetaData
        private static final String POSTGRESQL = "PostgreSQL";

        private final LocalDate value;

        public LocalDateArgument(final LocalDate value) {
            this.value = value;
        }

        @Override
        public void apply(final int position, final PreparedStatement statement, final StatementContext ctx) throws SQLException {
            final boolean isPostgreSQL = ctx != null &&
                                         ctx.getConnection() != null &&
                                         ctx.getConnection().getMetaData() != null &&
                                         POSTGRESQL.equalsIgnoreCase(ctx.getConnection().getMetaData().getDatabaseProductName());
            if (value != null && isPostgreSQL) {
                // This might work on MySQL as well, but let's avoid conversions if we don't have to
                // See also https://github.com/killbill/killbill/wiki/Date%2C-Datetime%2C-Timezone-and-time-Granularity-in-Kill-Bill
                statement.setDate(position, new java.sql.Date(value.toDate().getTime()));
            } else if (value != null) {
                // ISO8601 format
                statement.setString(position, value.toString());
            } else {
                statement.setNull(position, isPostgreSQL ? Types.DATE : Types.VARCHAR);
            }
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("LocalDateArgument");
            sb.append("{value=").append(value);
            sb.append('}');
            return sb.toString();
        }
    }

}
