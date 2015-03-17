/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.commons.jdbi.guice;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.killbill.commons.jdbi.argument.DateTimeArgumentFactory;
import org.killbill.commons.jdbi.argument.DateTimeZoneArgumentFactory;
import org.killbill.commons.jdbi.argument.LocalDateArgumentFactory;
import org.killbill.commons.jdbi.argument.UUIDArgumentFactory;
import org.killbill.commons.jdbi.log.Slf4jLogging;
import org.killbill.commons.jdbi.mapper.UUIDMapper;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TimingCollector;
import org.skife.jdbi.v2.tweak.ArgumentFactory;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.skife.jdbi.v2.tweak.SQLLog;
import org.skife.jdbi.v2.tweak.TransactionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class DBIProvider implements Provider<IDBI> {

    private static final Logger logger = LoggerFactory.getLogger(DBIProvider.class);

    private final DaoConfig config;
    private final DataSource ds;
    private final TransactionHandler transactionHandler;
    private final Set<ArgumentFactory> argumentFactorySet = new LinkedHashSet<ArgumentFactory>();
    private final Set<ResultSetMapper> resultSetMapperSet = new LinkedHashSet<ResultSetMapper>();

    private SQLLog sqlLog;
    private TimingCollector timingCollector;

    @Inject
    public DBIProvider(final DaoConfig config, final DataSource ds, final TransactionHandler transactionHandler) {
        this.config = config;
        this.ds = ds;
        this.transactionHandler = transactionHandler;
        setDefaultArgumentFactorySet();
        setDefaultResultSetMapperSet();
    }

    @Inject(optional = true)
    public void setArgumentFactorySet(final Set<ArgumentFactory> argumentFactorySet) {
        for (final ArgumentFactory argumentFactory : argumentFactorySet) {
            this.argumentFactorySet.add(argumentFactory);
        }
    }

    @Inject(optional = true)
    public void setResultSetMapperSet(final Set<ResultSetMapper> resultSetMapperSet) {
        for (final ResultSetMapper resultSetMapper : resultSetMapperSet) {
            this.resultSetMapperSet.add(resultSetMapper);
        }
    }

    @Inject(optional = true)
    public void setSqlLog(final SQLLog sqlLog) {
        this.sqlLog = sqlLog;
    }

    @Inject(optional = true)
    public void setTimingCollector(final TimingCollector timingCollector) {
        this.timingCollector = timingCollector;
    }

    @Override
    public IDBI get() {
        final DBI dbi = new DBI(ds);

        for (final ArgumentFactory argumentFactory : argumentFactorySet) {
            dbi.registerArgumentFactory(argumentFactory);
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
