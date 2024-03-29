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
package org.skife.jdbi.v2;

import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.skife.jdbi.v2.exceptions.StatementException;
import org.skife.jdbi.v2.exceptions.UnableToCreateStatementException;
import org.skife.jdbi.v2.sqlobject.stringtemplate.TestingStatementContext;
import org.skife.jdbi.v2.tweak.StatementLocator;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 */
@Category(JDBITests.class)
public class TestClasspathStatementLocator extends DBITestCase
{
    @Test
    public void testLocateNamedWithoutSuffix() throws Exception {
        Handle h = openHandle();
        h.createStatement("insert-keith").execute();
        assertEquals(1, h.select("select name from something").size());
    }

    @Test
    public void testLocateNamedWithSuffix() throws Exception {
        Handle h = openHandle();
        h.insert("insert-keith.sql");
        assertEquals(1, h.select("select name from something").size());
    }

    @Test
    public void testCommentsInExternalSql() throws Exception {
        Handle h = openHandle();
        h.insert("insert-eric-with-comments");
        assertEquals(1, h.select("select name from something").size());
    }

    @Test
    public void testNamedPositionalNamedParamsInPrepared() throws Exception {
        Handle h = openHandle();
        h.insert("insert-id-name", 3, "Tip");
        assertEquals(1, h.select("select name from something").size());
    }

    @Test
    public void testNamedParamsInExternal() throws Exception {
        Handle h = openHandle();
        h.createStatement("insert-id-name").bind("id", 1).bind("name", "Tip").execute();
        assertEquals(1, h.select("select name from something").size());
    }

    @Test
    public void testUsefulExceptionForBackTracing() throws Exception {
        Handle h = openHandle();

        try {
            h.createStatement("insert-id-name").bind("id", 1).execute();
            fail("should have raised an exception");
        }
        catch (StatementException e) {
            assertTrue(e.getMessage().contains("insert into something(id, name) values (:id, :name)"));
            assertTrue(e.getMessage().contains("insert into something(id, name) values (?, ?)"));
            assertTrue(e.getMessage().contains("insert-id-name"));
        }

    }

    @Test
    public void testTriesToParseNameIfNothingFound() throws Exception {
        Handle h = openHandle();
        try {
            h.insert("this-does-not-exist.sql");
            fail("Should have raised an exception");
        }
        catch (UnableToCreateStatementException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testCachesResultAfterFirstLookup() throws Exception {
        ClassLoader ctx_loader = Thread.currentThread().getContextClassLoader();
        final AtomicInteger load_count = new AtomicInteger(0);

        Thread.currentThread().setContextClassLoader(new ClassLoader() {
            @Override
            public InputStream getResourceAsStream(final String name) {

                // will be called twice, once for raw name, once for name + .sql
                InputStream in = super.getResourceAsStream(name);
                load_count.incrementAndGet();
                return in;
            }
        });

        Handle h = openHandle();
        h.execute("caches-result-after-first-lookup", 1, "Brian");
        assertThat(load_count.get(), equalTo(2)); // two lookups, name and name.sql

        h.execute("caches-result-after-first-lookup", 2, "Sean");
        assertThat(load_count.get(), equalTo(2)); // has not increased since previous

        Thread.currentThread().setContextClassLoader(ctx_loader);
    }

    @Test
    public void testCachesOriginalQueryWhenNotFound() throws Exception
    {
        StatementLocator statementLocator = new ClasspathStatementLocator();
        StatementContext statementContext = new TestingStatementContext(new HashMap<String, Object>()) {

            @Override
            public Class<?> getSqlObjectType() {
                return TestClasspathStatementLocator.class;
            }
        };

        String input = "missing query";
        String located = statementLocator.locate(input, statementContext);

        assertEquals(input, located); // first time just caches it

        located = statementLocator.locate(input, statementContext);

        assertEquals(input, located); // second time reads from cache
    }
}
