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

package org.killbill.commons.jdbi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;

import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;
import org.antlr.stringtemplate.language.AngleBracketTemplateLexer;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.StatementLocator;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

// Similar to StringTemplate3StatementLocator, but safe to use in conjunction with dbi#setStatementLocator
public class ReusableStringTemplate3StatementLocator implements StatementLocator {

    protected final StringTemplateGroup group;
    protected final StringTemplateGroup literals = new StringTemplateGroup("literals", AngleBracketTemplateLexer.class);
    protected final boolean treatLiteralsAsTemplates;

    public ReusableStringTemplate3StatementLocator(final Class baseClass) {
        this(mungify("/" + baseClass.getName()) + ".sql.stg", false, false);
    }

    public ReusableStringTemplate3StatementLocator(final String templateGroupFilePathOnClasspath) {
        this(templateGroupFilePathOnClasspath, false, false);
    }

    public ReusableStringTemplate3StatementLocator(final Class baseClass,
                                                   final boolean allowImplicitTemplateGroup,
                                                   final boolean treatLiteralsAsTemplates) {
        this(mungify("/" + baseClass.getName()) + ".sql.stg", allowImplicitTemplateGroup, treatLiteralsAsTemplates);
    }

    public ReusableStringTemplate3StatementLocator(final String templateGroupFilePathOnClasspath,
                                                   final boolean allowImplicitTemplateGroup,
                                                   final boolean treatLiteralsAsTemplates) {
        this.treatLiteralsAsTemplates = treatLiteralsAsTemplates;
        final InputStream ins = getClass().getResourceAsStream(templateGroupFilePathOnClasspath);
        if (allowImplicitTemplateGroup && ins == null) {
            this.group = new StringTemplateGroup("empty template group", AngleBracketTemplateLexer.class);
        } else if (ins == null) {
            throw new IllegalStateException("unable to find group file "
                                            + templateGroupFilePathOnClasspath
                                            + " on classpath");
        } else {
            final InputStreamReader reader = new InputStreamReader(ins);
            try {
                this.group = new StringTemplateGroup(reader, AngleBracketTemplateLexer.class);
                reader.close();
            } catch (IOException e) {
                throw new IllegalStateException("unable to load string template group " + templateGroupFilePathOnClasspath,
                                                e);
            }
        }
    }

    // Note! This code needs to be thread safe. We just synchronize the whole method for now, we could probably do better...
    public synchronized String locate(final String name, final StatementContext ctx) throws Exception {
        if (group.isDefined(name)) {
            final StringTemplate t = group.lookupTemplate(name);
            for (final Map.Entry<String, Object> entry : ctx.getAttributes().entrySet()) {
                t.setAttribute(entry.getKey(), entry.getValue());
            }
            final String sql = t.toString();

            // Reset the template attributes
            t.setAttributes(null);

            return sql;
        } else if (treatLiteralsAsTemplates) {
            // no template in the template group, but we want literals to be templates
            final String key = BaseEncoding.base64().encode(name.getBytes(Charsets.US_ASCII));
            if (!literals.isDefined(key)) {
                literals.defineTemplate(key, name);
            }
            final StringTemplate t = literals.lookupTemplate(key);
            for (final Map.Entry<String, Object> entry : ctx.getAttributes().entrySet()) {
                t.setAttribute(entry.getKey(), entry.getValue());
            }
            final String sql = t.toString();

            // Reset the template attributes
            t.setAttributes(null);

            return sql;
        } else {
            return name;
        }
    }

    private final static String sep = "/"; // *Not* System.getProperty("file.separator"), which breaks in jars

    private static String mungify(final String path) {
        return path.replaceAll("\\.", Matcher.quoteReplacement(sep));
    }
}
