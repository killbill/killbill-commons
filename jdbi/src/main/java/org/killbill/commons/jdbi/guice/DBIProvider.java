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

package org.killbill.commons.jdbi.guice;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.sql.DataSource;

import org.killbill.commons.jdbi.argument.DateTimeArgumentFactory;
import org.killbill.commons.jdbi.argument.DateTimeZoneArgumentFactory;
import org.killbill.commons.jdbi.argument.LocalDateArgumentFactory;
import org.killbill.commons.jdbi.argument.UUIDArgumentFactory;
import org.killbill.commons.jdbi.log.Slf4jLogging;
import org.killbill.commons.jdbi.mapper.UUIDMapper;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.ResultSetMapperFactory;
import org.skife.jdbi.v2.TimingCollector;
import org.skife.jdbi.v2.tweak.ArgumentFactory;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.skife.jdbi.v2.tweak.SQLLog;
import org.skife.jdbi.v2.tweak.StatementBuilderFactory;
import org.skife.jdbi.v2.tweak.StatementRewriter;
import org.skife.jdbi.v2.tweak.TransactionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBIProvider implements Provider<IDBI> {

    private static final Logger logger = LoggerFactory.getLogger(DBIProvider.class);

    private final DaoConfig config;
    private final DataSource ds;
    private final TransactionHandler transactionHandler;
    private final Set<ArgumentFactory> argumentFactorySet = new LinkedHashSet<>();
    private final Set<ResultSetMapperFactory> resultSetMapperFactorySet = new LinkedHashSet<>();
    private final Set<ResultSetMapper> resultSetMapperSet = new LinkedHashSet<>();

    private SQLLog sqlLog;
    private TimingCollector timingCollector;
    private StatementRewriter statementRewriter;
    private StatementBuilderFactory statementBuilderFactory;

    @Inject
    public DBIProvider(final DaoConfig config, final DataSource ds, final TransactionHandler transactionHandler) {
        this.config = config;
        this.ds = ds;
        this.transactionHandler = transactionHandler;
        setDefaultArgumentFactorySet();
        setDefaultResultSetMapperSet();
    }

    @Inject
    public void setArgumentFactorySet(@Nullable final Set<ArgumentFactory> argumentFactorySet) {
        if (argumentFactorySet != null) {
            this.argumentFactorySet.addAll(argumentFactorySet);
        }
    }

    @Inject
    public void setResultSetMapperFactorySet(@Nullable final Set<ResultSetMapperFactory> resultSetMapperFactorySet) {
        if (resultSetMapperFactorySet != null) {
            this.resultSetMapperFactorySet.addAll(resultSetMapperFactorySet);
        }
    }

    @Inject
    public void setResultSetMapperSet(@Nullable final Set<ResultSetMapper> resultSetMapperSet) {
        if (resultSetMapperSet != null) {
            this.resultSetMapperSet.addAll(resultSetMapperSet);
        }
    }

    @Inject
    public void setSqlLog(@Nullable final SQLLog sqlLog) {
        this.sqlLog = sqlLog;
    }

    @Inject
    public void setTimingCollector(@Nullable final TimingCollector timingCollector) {
        this.timingCollector = timingCollector;
    }

    @Inject
    public void setStatementRewriter(@Nullable final StatementRewriter statementRewriter) {
        this.statementRewriter = statementRewriter;
    }

    @Inject
    public void setStatementBuilderFactory(@Nullable final StatementBuilderFactory statementBuilderFactory) {
        this.statementBuilderFactory = statementBuilderFactory;
    }

    @Override
    public IDBI get() {
        final DBI dbi = new DBI(ds);

        if (statementRewriter != null) {
            dbi.setStatementRewriter(statementRewriter);
        }

        if (statementBuilderFactory != null) {
            dbi.setStatementBuilderFactory(statementBuilderFactory);
        }

        for (final ArgumentFactory argumentFactory : argumentFactorySet) {
            dbi.registerArgumentFactory(argumentFactory);
        }

        for (final ResultSetMapperFactory resultSetMapperFactory : resultSetMapperFactorySet) {
            dbi.registerMapper(resultSetMapperFactory);
        }

        for (final ResultSetMapper resultSetMapper : resultSetMapperSet) {
            dbi.registerMapper(resultSetMapper);
        }

        if (transactionHandler != null) {
            dbi.setTransactionHandler(transactionHandler);
        }

        if (sqlLog != null) {
            dbi.setSQLLog(sqlLog);
        } else if (config != null) {
            final Slf4jLogging sqlLog = new Slf4jLogging(logger, config.getLogLevel());
            dbi.setSQLLog(sqlLog);
        }

        if (timingCollector != null) {
            dbi.setTimingCollector(timingCollector);
        }

        return dbi;
    }

    protected void setDefaultArgumentFactorySet() {
        argumentFactorySet.add(new UUIDArgumentFactory());
        argumentFactorySet.add(new DateTimeZoneArgumentFactory());
        argumentFactorySet.add(new DateTimeArgumentFactory());
        argumentFactorySet.add(new LocalDateArgumentFactory());
    }

    protected void setDefaultResultSetMapperSet() {
        resultSetMapperSet.add(new UUIDMapper());
    }
}
