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

package org.killbill.commons.embeddeddb.mysql;

import java.io.IOException;

/**
 * Delegates to a real MySQL database. This can be used for debugging.
 */
public class MySQLStandaloneDB extends MySQLEmbeddedDB {

    public MySQLStandaloneDB(final String databaseName) {
        this(databaseName, "root", null);
    }

    public MySQLStandaloneDB(final String databaseName, final String username, final String password) {
        this(databaseName, username, password, "jdbc:mysql://localhost:3306/" + databaseName + "?createDatabaseIfNotExist=true&allowMultiQueries=true");
    }

    public MySQLStandaloneDB(final String databaseName, final String username, final String password, final String jdbcConnectionString) {
        super(databaseName, username, password, jdbcConnectionString);
    }

    @Override
    public void start() throws IOException {
        started.set(true);
        refreshTableNames();
    }

    @Override
    public void stop() throws IOException {
        started.set(false);
    }

    @Override
    public String getCmdLineConnectionString() {
        return String.format("mysql -u%s -p%s -P%s %s", username, password, port, databaseName);
    }
}
