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

package org.killbill.commons.embeddeddb.mysql;

import java.sql.SQLException;
import java.util.Properties;

import org.mariadb.jdbc.MariaDbDataSource;
import org.mariadb.jdbc.UrlParser;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;

public class KillBillMariaDbDataSource extends MariaDbDataSource {

    private static final Joiner.MapJoiner mapJoiner = Joiner.on('&').withKeyValueSeparator("=");

    private String url;

    private Boolean cachePrepStmts;
    private Integer prepStmtCacheSize;
    private Integer prepStmtCacheSqlLimit;
    private Boolean useServerPrepStmts;

    @Override
    public void setUrl(final String url) throws SQLException {
        this.url = url;
        super.setUrl(url);
        // If called by PropertyElf.setTargetFromProperties (HikariCP), we need to take into account our extra properties
        updateUrlIfNeeded();
    }

    // See HikariDataSourceBuilder and https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration

    public void setCachePrepStmts(final boolean cachePrepStmts) throws SQLException {
        this.cachePrepStmts = cachePrepStmts;
        updateUrlIfNeeded();
    }

    public void setPrepStmtCacheSize(final int prepStmtCacheSize) throws SQLException {
        this.prepStmtCacheSize = prepStmtCacheSize;
        updateUrlIfNeeded();
    }

    public void setPrepStmtCacheSqlLimit(final int prepStmtCacheSqlLimit) throws SQLException {
        this.prepStmtCacheSqlLimit = prepStmtCacheSqlLimit;
        updateUrlIfNeeded();
    }

    // Note! New default is now false (was true before 1.6.0)
    public void setUseServerPrepStmts(final boolean useServerPrepStmts) throws SQLException {
        this.useServerPrepStmts = useServerPrepStmts;
        updateUrlIfNeeded();
    }

    private void updateUrlIfNeeded() throws SQLException {
        if (url == null) {
            final UrlParser urlParser = initializeAndGetUrlParser();
            if (urlParser != null) {
                url = urlParser.getInitialUrl();
            }
        }

        if (url != null) {
            this.url = buildUpdatedUrl(this.url);
            super.setUrl(url);
        }
    }

    // No easy way to set MariaDB options at runtime besides updating the JDBC url
    @VisibleForTesting
    String buildUpdatedUrl(final String url) throws SQLException {
        final Properties props = new Properties();
        UrlParser.parse(url, props);
        if (cachePrepStmts != null && !props.containsKey("cachePrepStmts")) {
            props.put("cachePrepStmts", cachePrepStmts);
        }
        if (prepStmtCacheSize != null && !props.containsKey("prepStmtCacheSize")) {
            props.put("prepStmtCacheSize", prepStmtCacheSize);
        }
        if (prepStmtCacheSqlLimit != null && !props.containsKey("prepStmtCacheSqlLimit")) {
            props.put("prepStmtCacheSqlLimit", prepStmtCacheSqlLimit);
        }
        if (useServerPrepStmts != null && !props.containsKey("useServerPrepStmts")) {
            props.put("useServerPrepStmts", useServerPrepStmts);
        }

        final int separator = url.indexOf("//");
        final String urlSecondPart = url.substring(separator + 2);
        final int paramIndex = urlSecondPart.indexOf("?");

        final String baseUrl = paramIndex > 0 ? url.substring(0, separator + 2 + paramIndex) : url;
        return props.isEmpty() ? baseUrl : baseUrl + "?" + mapJoiner.join(props);
    }

    @VisibleForTesting
    public UrlParser initializeAndGetUrlParser() throws SQLException {
        super.initialize();
        return super.getUrlParser();
    }
}
