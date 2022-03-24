/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.commons.jdbi.metrics;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.api.Timer;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.TimingCollector;

public class KillBillTimingCollector implements TimingCollector {

    private final MetricRegistry registry;

    public KillBillTimingCollector(final MetricRegistry registry) {
        this.registry = registry;
    }

    public void collect(final long elapsedTime, final StatementContext ctx) {
        final Timer timer = this.getTimer(ctx);
        timer.update(elapsedTime, TimeUnit.NANOSECONDS);
    }

    private Timer getTimer(final StatementContext ctx) {
        return this.registry.timer(getStatementName(ctx));
    }

    private String getStatementName(final StatementContext ctx) {
        final String rawSql = ctx.getRawSql();
        if (rawSql == null || rawSql.isEmpty()) {
            return "sql.empty";
        }

        final Class<?> clazz = ctx.getSqlObjectType();
        final Method method = ctx.getSqlObjectMethod();
        if (clazz != null) {
            final String group = clazz.getPackage().getName();
            final String name = clazz.getSimpleName();
            final String type = method == null ? rawSql : method.getName();
            return String.format("%s.%s.%s", group, name, type);
        }

        final int colon = rawSql.indexOf(':');
        if (colon == -1) {
            // No package? Just return the name, JDBI figured out somehow on how to find the raw sql for this statement.
            return String.format("%s.%s.%s", "sql", "raw", rawSql);
        }

        final String group = rawSql.substring(0, colon);
        final String name = rawSql.substring(colon + 1);
        return String.format("%s.%s", group, name);
    }
}
