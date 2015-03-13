/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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

package org.killbill.queue;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import javax.sql.DataSource;

import org.killbill.commons.jdbi.argument.DateTimeArgumentFactory;
import org.killbill.commons.jdbi.argument.DateTimeZoneArgumentFactory;
import org.killbill.commons.jdbi.argument.LocalDateArgumentFactory;
import org.killbill.commons.jdbi.argument.UUIDArgumentFactory;
import org.killbill.commons.jdbi.mapper.UUIDMapper;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InTransaction {

    private static final Logger logger = LoggerFactory.getLogger(InTransaction.class);

    public static <K, R> R execute(final Connection connection, final InTransactionHandler<K, R> handler, final Class<K> klass) {
        final DBI dbi = buildDDBI(connection);

        final Handle handle = dbi.open();
        try {
            final K transactional = handle.attach(klass);
            return handler.withSqlDao(transactional);
        } catch (final Exception e) {
            logger.error("Failed to process transaction", e);
            return null;
        } finally {
            // We do not release the connection -- client is responsible for closing it
            // h.close();
        }
    }

    public static DBI buildDDBI(final Connection connection) {
        final DataSourceWithDelegate dataSource = new DataSourceWithDelegate(connection);
        return buildDDBI(dataSource);
    }

    public static DBI buildDDBI(final DataSource dataSource) {
        final DBI dbi = new DBI(dataSource);
        dbi.registerArgumentFactory(new UUIDArgumentFactory());
        dbi.registerArgumentFactory(new DateTimeZoneArgumentFactory());
        dbi.registerArgumentFactory(new DateTimeArgumentFactory());
        dbi.registerArgumentFactory(new LocalDateArgumentFactory());
        dbi.registerMapper(new UUIDMapper());
        return dbi;
    }

    public static interface InTransactionHandler<K, R> {

        public R withSqlDao(final K transactional) throws Exception;
    }

    private static final class DataSourceWithDelegate implements DataSource {

        private final Connection connection;

        public DataSourceWithDelegate(final Connection connection) {
            this.connection = connection;
        }

        public Connection getConnection() throws SQLException {
            return connection;
        }

        public Connection getConnection(final String username, final String password) throws SQLException {
            return connection;
        }

        public PrintWriter getLogWriter() throws SQLException {
            throw new UnsupportedOperationException();
        }

        public void setLogWriter(final PrintWriter out) throws SQLException {
            throw new UnsupportedOperationException();
        }

        public void setLoginTimeout(final int seconds) throws SQLException {
            throw new UnsupportedOperationException();
        }

        public int getLoginTimeout() throws SQLException {
            throw new UnsupportedOperationException();
        }

        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new UnsupportedOperationException();
        }

        public <T> T unwrap(final Class<T> iface) throws SQLException {
            throw new UnsupportedOperationException();
        }

        public boolean isWrapperFor(final Class<?> iface) throws SQLException {
            throw new UnsupportedOperationException();
        }
    }
}
