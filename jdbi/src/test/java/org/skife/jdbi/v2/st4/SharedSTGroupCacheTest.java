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

import java.io.File;
import java.nio.charset.Charset;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.skife.jdbi.v2.JDBITests;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ST4StatementLocator;

import com.google.common.io.Files;

import static org.assertj.core.api.Assertions.assertThat;

public class SharedSTGroupCacheTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Test
    @Category(JDBITests.class)
    public void testUseAndDontUseSTGroupCache() throws Exception {
        String sql;
        File tmp = File.createTempFile("test", ".stg");

        StatementContext ctx = Mockito.mock(StatementContext.class);

        // first write, and loaded into the cache
        Files.write("test() ::= <<chirp>>".getBytes(UTF_8), tmp);
        sql = ST4StatementLocator.forURL(ST4StatementLocator.UseSTGroupCache.YES, tmp.toURI().toURL())
                                 .locate("test", ctx);
        assertThat(sql).isEqualTo("chirp");

        // change the template, but use cache which should not see changes
        Files.write("test() ::= <<ribbit>>".getBytes(UTF_8), tmp);
        sql = ST4StatementLocator.forURL(ST4StatementLocator.UseSTGroupCache.YES, tmp.toURI().toURL())
                                 .locate("test", ctx);
        assertThat(sql).isEqualTo("chirp");

        // change the template and don't use cache, which should load changes
        Files.write("test() ::= <<meow>>".getBytes(UTF_8), tmp);
        sql = ST4StatementLocator.forURL(ST4StatementLocator.UseSTGroupCache.NO, tmp.toURI().toURL())
                                 .locate("test", ctx);
        assertThat(sql).isEqualTo("meow");

        // change template again, don't use cache again, we should see the change
        Files.write("test() ::= <<woof>>".getBytes(UTF_8), tmp);
        sql = ST4StatementLocator.forURL(ST4StatementLocator.UseSTGroupCache.NO, tmp.toURI().toURL())
                                 .locate("test", ctx);
        assertThat(sql).isEqualTo("woof");
    }
}
