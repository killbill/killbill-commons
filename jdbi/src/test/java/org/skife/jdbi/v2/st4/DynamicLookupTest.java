/*
 * Copyright 2004-2014 Brian McCallister
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

package org.skife.jdbi.v2.st4;

import java.util.List;

import javax.annotation.Nullable;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.JDBIQuarantineTests;
import org.skife.jdbi.v2.JDBITests;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ST4StatementLocator;
import org.skife.jdbi.v2.util.IntegerMapper;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupString;

import com.google.common.base.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicLookupTest {

    @Rule
    public H2Rule h2 = new H2Rule();

    @Test
    @Category(JDBIQuarantineTests.class)
    public void testFancyDynamicTemplateLookups() throws Exception {
        final DBI dbi = new DBI(h2);
        /* [OPTIMIZATION] Disabled to optimization
        dbi.setStatementLocator(new ST4StatementLocator(new Function<StatementContext, STGroup>() {
            @Nullable
            @Override
            public STGroup apply(@Nullable final StatementContext ctx) {
                return new STGroupString(ctx.getAttribute("template")
                                            .toString());
            }
        }));
        */

        Handle h = dbi.open();
        try {
            h.define("template", "create(name) ::= <% create table <name> (id int primary key) %>");

            h.createStatement("create")
             .define("name", "something")
             .execute();

            h.execute("insert into something (id) values (1)");

            final List<Integer> ids = h.createQuery("select")
                                       .define("template", "select() ::= <% select id from something %>")
                                       .map(new IntegerMapper())
                                       .list();
            assertThat(ids).containsExactly(1);
        } finally {
            h.close();
        }
    }
}
