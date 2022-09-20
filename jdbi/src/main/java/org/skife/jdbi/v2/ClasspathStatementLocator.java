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

import org.skife.jdbi.v2.exceptions.UnableToCreateStatementException;
import org.skife.jdbi.v2.tweak.StatementLocator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * looks for [name], then [name].sql on the classpath
 */
public class ClasspathStatementLocator implements StatementLocator
{
    private static final Pattern MULTILINE_COMMENTS = Pattern.compile("/\\*.*?\\*/");

    private final Map<String, String> found = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Very basic sanity test to see if a string looks like it might be sql
     */
    public static boolean looksLikeSql(final String sql)
    {
        final String local = left(stripStart(sql), 8).toLowerCase();
        return local.startsWith("insert ")
               || local.startsWith("update ")
               || local.startsWith("select ")
               || local.startsWith("call ")
               || local.startsWith("delete ")
               || local.startsWith("create ")
               || local.startsWith("alter ")
               || local.startsWith("merge ")
               || local.startsWith("replace ")
               || local.startsWith("drop ");
    }

    /**
     * If the passed in name doesn't look like SQL it will search the classpath for a file
     * which looks like the provided name.
     * <p/>
     * The "looks like" algorithm is not very sophisticated, it basically looks for the string
     * to begin with insert, update, select, call, delete, create, alter, or drop followed
     * by a space.
     * <p/>
     * If no resource is found using the passed in string, the string s returned as-is
     *
     * @param name Name or statement literal
     *
     * @return SQL to execute (which will go to a StatementRRewrter first)
     *
     * @throws UnableToCreateStatementException
     *          if an IOException occurs reading a found resource
     */
    @Override
    @SuppressWarnings("PMD.EmptyCatchBlock")
    public String locate(final String name, final StatementContext ctx) throws Exception {
        final String cache_key;
        if (ctx.getSqlObjectType() != null) {
            cache_key = '/' + mungify(ctx.getSqlObjectType().getName() + '.' + name) + ".sql";
        } else {
            cache_key = name;
        }

        if (found.containsKey(cache_key)) {
            return found.get(cache_key);
        }

        if (looksLikeSql(name)) {
            // No need to cache individual SQL statements that don't cause us to search the classpath
            return name;
        }
        final ClassLoader loader = selectClassLoader();
        BufferedReader reader = null;
        try {
            InputStream in_stream = loader.getResourceAsStream(name);
            if (in_stream == null) {
                in_stream = loader.getResourceAsStream(name + ".sql");
            }

            if (in_stream == null && ctx.getSqlObjectType() != null) {
                final String filename = '/' + mungify(ctx.getSqlObjectType().getName() + '.' + name) + ".sql";
                in_stream = loader.getResourceAsStream(filename);
                if (in_stream == null) {
                    in_stream = ctx.getSqlObjectType().getResourceAsStream(filename);
                }
            }

            if (in_stream == null) {
                found.put(cache_key, name);
                return name;
            }

            final StringBuffer buffer = new StringBuffer();
            reader = new BufferedReader(new InputStreamReader(in_stream, StandardCharsets.UTF_8));
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    if (isComment(line)) {
                        // comment
                        continue;
                    }
                    buffer.append(line).append(" ");
                }
            }
            catch (final IOException e) {
                throw new UnableToCreateStatementException(e.getMessage(), e, ctx);
            }

            final String sql = MULTILINE_COMMENTS.matcher(buffer).replaceAll("");
            found.put(cache_key, sql);
            return sql;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            }
            catch (final IOException e) {
                // nothing we can do here :-(
            }
        }
    }

    /**
     * There *must* be a better place to put this without creating a helpers class just for it
     */
    private static ClassLoader selectClassLoader() {
        final ClassLoader loader;
        if (Thread.currentThread().getContextClassLoader() != null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        else {
            loader = ClasspathStatementLocator.class.getClassLoader();
        }
        return loader;
    }

    private static boolean isComment(final String line) {
        return line.startsWith("#") || line.startsWith("--") || line.startsWith("//");
    }

    private static final String SEP = "/"; // *Not* System.getProperty("file.separator"), which breaks in jars

    private static String mungify(final String path)
    {
        return path.replaceAll("\\.", Matcher.quoteReplacement(SEP));
    }


    // (scs) Logic copied from commons-lang3 3.1 with minor edits, per discussion on commit 023a14ade2d33bf8ccfa0f68294180455233ad52
    private static String stripStart(final String str) {
        final int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return "";
        }
        int start = 0;
        while (start != strLen && Character.isWhitespace(str.charAt(start))) {
            start++;
        }
        return str.substring(start);
    }

    private static String left(final String str, final int len) {
        if (str == null || len < 0) {
            return "";
        }
        if (str.length() <= len) {
            return str;
        }
        return str.substring(0, len);
    }
}
