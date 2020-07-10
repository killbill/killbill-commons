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

package org.killbill.commons.embeddeddb;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

public class GenericStandaloneDB extends EmbeddedDB {

    protected final AtomicBoolean started = new AtomicBoolean(false);

    public GenericStandaloneDB(final String databaseName, final String username, final String password, final String jdbcConnectionString) {
        super(databaseName, username, password, jdbcConnectionString);
    }

    @Override
    public DBEngine getDBEngine() {
        return DBEngine.GENERIC;
    }

    @Override
    public void initialize() throws IOException, SQLException {
    }

    @Override
    public void start() throws IOException {
        started.set(true);
        refreshTableNames();
    }

    @Override
    public void refreshTableNames() throws IOException {
    }

    @Override
    public void stop() throws IOException {
        started.set(false);
        super.stop();
    }
}
