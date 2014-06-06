/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import org.killbill.commons.jdbi.argument.DateTimeArgumentFactory;
import org.killbill.commons.jdbi.argument.DateTimeZoneArgumentFactory;
import org.killbill.commons.jdbi.argument.EnumArgumentFactory;
import org.killbill.commons.jdbi.argument.LocalDateArgumentFactory;
import org.killbill.commons.jdbi.argument.UUIDArgumentFactory;
import org.killbill.commons.jdbi.log.Slf4jLogging;
import org.killbill.commons.jdbi.mapper.UUIDMapper;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TimingCollector;
import org.skife.jdbi.v2.tweak.SQLLog;
import org.skife.jdbi.v2.tweak.TransactionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.sql.DataSource;

public class DBIProvider implements Provider<IDBI> {

    private static final Logger logger = LoggerFactory.getLogger(DBIProvider.class);

    private final DaoConfig config;
    private final DataSource ds;
    private SQLLog sqlLog;
    private TimingCollector timingCollector;

    @Inject
    public DBIProvider(final DaoConfig config) {
        this.config = config;
        this.ds = new DataSourceProvider(config).get();
    }

    public DBIProvider(final DataSource ds) {
        this.config = null;
        this.ds = ds;
    }

    @com.google.inject.Inject(optional = true)
    public void setSqlLog(final SQLLog sqlLog) {
        this.sqlLog = sqlLog;
    }

    @com.google.inject.Inject(optional = true)
    public void setTimingCollector(final TimingCollector timingCollector) {
        this.timingCollector = timingCollector;
    }

    @Override
    public IDBI get() {
        final DBI dbi = new DBI(ds);
        dbi.registerArgumentFactory(new UUIDArgumentFactory());
        dbi.registerArgumentFactory(new DateTimeZoneArgumentFactory());
        dbi.registerArgumentFactory(new DateTimeArgumentFactory());
        dbi.registerArgumentFactory(new LocalDateArgumentFactory());
        dbi.registerArgumentFactory(new EnumArgumentFactory());
        dbi.registerMapper(new UUIDMapper());

        if (config != null && config.getTransactionHandlerClass() != null) {
            logger.info("Using " + config.getTransactionHandlerClass() + " as a transaction handler class");
            try {
                dbi.setTransactionHandler((TransactionHandler) Class.forName(config.getTransactionHandlerClass()).newInstance());
            } catch (Exception e) {
                throw new RuntimeException("Error while trying to set the transaction handler to " + config.getTransactionHandlerClass(), e);
            }
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
}
