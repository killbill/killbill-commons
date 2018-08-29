/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.commons.embeddeddb.spanner;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.killbill.commons.embeddeddb.GenericStandaloneDB;

import nl.topicus.jdbc.CloudSpannerDataSource;

/**
 * Delegates to a real Spanner database.
 */
public class SpannerDB extends GenericStandaloneDB {

    private final String projectId;
    private final String instanceId;
    private final String privateKeyPath;

    public SpannerDB(final String projectId, final String instanceId, final String databaseName, final String privateKeyPath) {
        super(databaseName, null, null, "jdbc:cloudspanner://localhost/" + databaseName);
        this.projectId = projectId;
        this.instanceId = instanceId;
        this.privateKeyPath = privateKeyPath;
    }

    @Override
    public DBEngine getDBEngine() {
        return DBEngine.SPANNER;
    }

    @Override
    public void initialize() throws IOException, SQLException {
        super.initialize();
        final CloudSpannerDataSource subject = new CloudSpannerDataSource();
        this.dataSource = subject;
        subject.setProjectId(projectId);
        subject.setInstanceId(instanceId);
        subject.setDatabase(databaseName);
        subject.setPvtKeyPath(privateKeyPath);
    }

    @Override
    public void refreshTableNames() throws IOException {
        final String query = "select table_name from information_schema.tables where table_catalog = '' and table_schema = '';";
        try {
            executeQuery(query, new ResultSetJob() {
                @Override
                public void work(final ResultSet resultSet) throws SQLException {
                    allTables.clear();
                    while (resultSet.next()) {
                        allTables.add(resultSet.getString(1));
                    }
                }
            });
        } catch (final SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public String getCmdLineConnectionString() {
        return "gcloud";
    }
}
