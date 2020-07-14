/*
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

package org.killbill.commons.jdbi.transaction;

import java.sql.SQLException;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionIsolationLevel;
import org.skife.jdbi.v2.exceptions.TransactionFailedException;
import org.skife.jdbi.v2.tweak.TransactionHandler;
import org.skife.jdbi.v2.tweak.transactions.DelegatingTransactionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A TransactionHandler that automatically retries transactions that fail due to
 * serialization failures or innodb wait lock timeout, which can generally be resolved by automatically
 * retrying the transaction.  Any TransactionCallback used under this runner
 * should be aware that it may be invoked multiple times.
 */
public class RestartTransactionRunner extends DelegatingTransactionHandler implements TransactionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestartTransactionRunner.class);

    private static final String SQLSTATE_TXN_SERIALIZATION_FAILED = "40001";
    private static final String SQLSTATE_INNODB_WAIT_LOCK_TIMEOUT_EXCEEDED = "41000";

    private final Configuration configuration;

    public RestartTransactionRunner(final TransactionHandler delegate) {
        this(new Configuration(), delegate);
    }

    public RestartTransactionRunner(final Configuration configuration, final TransactionHandler delegate) {
        super(delegate);
        this.configuration = configuration;
    }

    @Override
    public <ReturnType> ReturnType inTransaction(final Handle handle, final TransactionCallback<ReturnType> callback) {
        int retriesRemaining = configuration.maxRetries;

        while (true) {
            try {
                return getDelegate().inTransaction(handle, callback);
            } catch (final RuntimeException e) {
                if (!isSqlState(configuration.serializationFailureSqlStates, e) || --retriesRemaining <= 0) {
                    throw e;
                }

                if (e.getCause() instanceof SQLException) {
                    final String sqlState = ((SQLException) e.getCause()).getSQLState();
                    log.warn("Restarting transaction due to SQLState {}, retries remaining {}", sqlState, retriesRemaining);
                } else {
                    log.warn("Restarting transaction due to {}, retries remaining {}", e.toString(), retriesRemaining);
                }
            }
        }
    }

    @Override
    public <ReturnType> ReturnType inTransaction(final Handle handle,
                                                 final TransactionIsolationLevel level,
                                                 final TransactionCallback<ReturnType> callback) {
        final TransactionIsolationLevel initial = handle.getTransactionIsolationLevel();
        try {
            handle.setTransactionIsolation(level);
            return inTransaction(handle, callback);
        } finally {
            handle.setTransactionIsolation(initial);
        }
    }

    /**
     * Returns true iff the Throwable or one of its causes is an SQLException whose SQLState begins
     * with the passed state.
     */
    protected boolean isSqlState(final String[] expectedSqlStates, Throwable throwable) {
        do {
            if (throwable instanceof SQLException) {
                final String sqlState = ((SQLException) throwable).getSQLState();

                if (sqlState != null) {
                    for (final String expectedSqlState : expectedSqlStates) {
                        if (sqlState.startsWith(expectedSqlState)) {
                            return true;
                        }
                    }
                }
            }
        } while ((throwable = throwable.getCause()) != null);

        return false;
    }

    public static class Configuration {

        private final int maxRetries;
        private final String[] serializationFailureSqlStates;

        public Configuration() {
            this(5, new String[]{SQLSTATE_TXN_SERIALIZATION_FAILED, SQLSTATE_INNODB_WAIT_LOCK_TIMEOUT_EXCEEDED});
        }

        private Configuration(final int maxRetries, final String[] serializationFailureSqlStates) {
            this.maxRetries = maxRetries;
            this.serializationFailureSqlStates = serializationFailureSqlStates;
        }

        public Configuration withMaxRetries(final int maxRetries) {
            return new Configuration(maxRetries, serializationFailureSqlStates);
        }

        public Configuration withSerializationFailureSqlState(final String[] serializationFailureSqlState) {
            return new Configuration(maxRetries, serializationFailureSqlState);
        }
    }
}
