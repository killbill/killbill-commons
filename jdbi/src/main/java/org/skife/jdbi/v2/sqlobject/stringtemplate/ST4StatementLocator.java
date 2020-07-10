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

package org.skife.jdbi.v2.sqlobject.stringtemplate;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.StatementLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STErrorListener;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;
import org.stringtemplate.v4.compiler.CompiledST;
import org.stringtemplate.v4.misc.ErrorManager;
import org.stringtemplate.v4.misc.STMessage;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;

public class ST4StatementLocator implements StatementLocator {

    private static final Logger logger = LoggerFactory.getLogger(ST4StatementLocator.class);

    private static final ErrorManager ERROR_MANAGER = new ErrorManager(new SLF4JSTErrorListener());

    private static final Map<String, STGroup> CACHE = new ConcurrentHashMap<String, STGroup>();
    private static final String COMPOSITE_KEY_SEPARATOR = "___#___";

    @VisibleForTesting
    final Map<String, String> locatedSqlCache = new ConcurrentHashMap<String, String>();

    private final STGroup group;

    public ST4StatementLocator(final STGroup group) {
        this.group = group;
    }

    /**
     * Obtains a locator based on a classpath path, using a global template group CACHE.
     */
    public static StatementLocator fromClasspath(final String path) {
        return fromClasspath(UseSTGroupCache.YES, path);
    }

    /**
     * Obtains a locator based on a classpath path. Allows flag to indicate whether the global STGroup CACHE should be
     * used. In general, the only reasons to NOT use the CACHE are: (1) if you are fiddling with the templates during development
     * and want to make changes without having to restart the server; and (2) if you use something like the tomcat
     * deployer to push new versions without restarting the JVM which causes static maps to leak memory.
     */
    public static StatementLocator fromClasspath(final UseSTGroupCache useCache, final String path) {
        return forURL(useCache, ST4StatementLocator.class.getResource(path));
    }

    /**
     * Obtains a locator based on the type passed in, using a global template group CACHE.
     * <p>
     * STGroup is loaded from the classpath via sqlObjectType.getResource( ), such that names line
     * up with the package and class, so: com.example.Foo will look for /com/example/Foo.sql.stg . Inner classes
     * are seperated in the file name by a '.' not a '$', so com.example.Foo.Bar (Bar is an inner class of Foo) would
     * be at /com/example/Foo.Bar.sql.stg .
     */
    public static StatementLocator forType(final Class sqlObjectType) {
        return forType(UseSTGroupCache.YES, sqlObjectType);
    }

    /**
     * Obtains a locator based on the type passed in. Allows flag to indicate whether the global STGroup CACHE should be
     * used. In general, the only reasons to NOT use the CACHE are: (1) if you are fiddling with the templates during development
     * and want to make changes without having to restart the server; and (2) if you use something like the tomcat
     * deployer to push new versions without restarting the JVM which causes static maps to leak memory.
     * <p>
     * STGroup is loaded from the classpath via sqlObjectType.getResource( ), such that names line
     * up with the package and class, so: com.example.Foo will look for /com/example/Foo.sql.stg . Inner classes
     * are seperated in the file name by a '.' not a '$', so com.example.Foo.Bar (Bar is an inner class of Foo) would
     * be at /com/example/Foo.Bar.sql.stg .
     */
    public static StatementLocator forType(final UseSTGroupCache useCache, final Class sqlObjectType) {
        return forURL(useCache, classToUrl(sqlObjectType));
    }

    public static StatementLocator forURL(final UseSTGroupCache useCache, final URL url) {
        final STGroup stg;
        if (useCache == UseSTGroupCache.YES) {
            stg = computeIfAbsent(CACHE, url.toString(), new Function<String, STGroup>() {
                @Override
                public STGroup apply(final String u) {
                    return urlToSTGroup(u);
                }
            });
        } else {
            stg = urlToSTGroup(url.toString());
        }

        return new ST4StatementLocator(stg);
    }

    /**
     * Create a statement locator intended for setting on a DBI or Handle instance which will
     * lookup a template group to use based on the name of the sql object type for a particular query, using
     * the same logic as {@link UseST4StatementLocator}.
     */
    public static StatementLocator perType(final UseSTGroupCache useCache) {
        return perType(useCache, new STGroup('<', '>'));
    }

    /**
     * Create a statement locator intended for setting on a DBI or Handle instance which will
     * lookup a template group to use based on the name of the sql object type for a particular query, using
     * the same logic as {@link UseST4StatementLocator}.
     * <p>
     * Supports a fallback template group for statements created not using a sql object.
     */
    public static StatementLocator perType(final UseSTGroupCache useCache, final String fallbackTemplateGroupPath) {
        return perType(useCache, ST4StatementLocator.class.getResource(fallbackTemplateGroupPath));
    }

    /**
     * Create a statement locator intended for setting on a DBI or Handle instance which will
     * lookup a template group to use based on the name of the sql object type for a particular query, using
     * the same logic as {@link UseST4StatementLocator}.
     * <p>
     * Supports a fallback template group for statements created not using a sql object.
     */
    public static StatementLocator perType(final UseSTGroupCache useCache, final URL baseTemplate) {
        return perType(useCache, urlToSTGroup(baseTemplate));
    }

    /**
     * Create a statement locator intended for setting on a DBI or Handle instance which will
     * lookup a template group to use based on the name of the sql object type for a particular query, using
     * the same logic as {@link UseST4StatementLocator}.
     * <p>
     * Supports a fallback template group for statements created not using a sql object.
     */
    public static StatementLocator perType(final UseSTGroupCache useCache, final STGroup fallbackTemplateGroup) {
        final StatementLocator fallback = new ST4StatementLocator(fallbackTemplateGroup);

        if (useCache == UseSTGroupCache.YES) {
            final Map<Class<?>, StatementLocator> sqlObjectCache = new ConcurrentHashMap<Class<?>, StatementLocator>();
            return new StatementLocator() {
                @Override
                public String locate(final String name, final StatementContext ctx) throws Exception {
                    if (ctx.getSqlObjectType() != null) {
                        final StatementLocator sl = computeIfAbsent(sqlObjectCache, ctx.getSqlObjectType(), new Function<Class<?>, StatementLocator>() {
                            @Override
                            public StatementLocator apply(final Class<?> c) {
                                return ST4StatementLocator.forType(useCache, ctx.getSqlObjectType());
                            }
                        });
                        return sl.locate(name, ctx);
                    } else {
                        return fallback.locate(name, ctx);
                    }
                }
            };
        } else {
            // if we are not caching, let's not cache the lookup of the template group either!
            return new StatementLocator() {
                @Override
                public String locate(final String name, final StatementContext ctx) throws Exception {
                    if (ctx.getSqlObjectType() != null) {
                        return ST4StatementLocator.forType(useCache, ctx.getSqlObjectType()).locate(name, ctx);
                    } else {
                        return fallback.locate(name, ctx);
                    }
                }
            };
        }
    }

    private static URL classToUrl(final Class c) {
        // handle naming of inner classes as Outer.Inner.sql.stg instead of Outer$Inner.sql.stg
        final String fullName = c.getName();
        final String pkg = c.getPackage().getName();
        final String className = fullName.substring(pkg.length() + 1, fullName.length()).replace('$', '.');
        return c.getResource(className + ".sql.stg");
    }

    private static STGroup urlToSTGroup(final String u) {
        try {
            return urlToSTGroup(new URL(u));
        } catch (final MalformedURLException e) {
            throw new IllegalStateException("a URL failed to roundtrip from a string!", e);
        }
    }

    private static STGroup urlToSTGroup(final URL u) {
        return new STGroupFileWithThreadSafeLoading(u, "UTF-8", '<', '>');
    }

    private static <K, V> V computeIfAbsent(final Map<K, V> map, final K key, final Function<K, V> mappingFunction) {
        V v;
        final V newValue;
        if ((v = map.get(key)) == null && (newValue = mappingFunction.apply(key)) != null) {
            if ((v = putIfAbsent(map, key, newValue)) == null) {
                return newValue;
            } else {
                return v;
            }
        } else {
            return v;
        }
    }

    private static <K, V> V putIfAbsent(final Map<K, V> map, final K key, final V value) {
        V v = map.get(key);
        if (v == null) {
            v = map.put(key, value);
        }

        return v;
    }

    @Override
    public String locate(final String name, final StatementContext ctx) throws Exception {
        final Iterator<Entry<String, Object>> entryIterator = ctx.getAttributes().entrySet().iterator();
        if (!entryIterator.hasNext()) {
            // Easiest: no attribute defined, always the same SQL
            return locateFromCache(name, name, ctx);
        } else {
            final Entry<String, Object> attribute1 = entryIterator.next();
            if (!entryIterator.hasNext()) {
                final String compositeKey = buildCompositeCacheKey(name, attribute1);
                return locateFromCache(compositeKey, name, ctx);
            } else {
                final Entry<String, Object> attribute2 = entryIterator.next();
                if (!entryIterator.hasNext()) {
                    // 2 attributes defined -- worth optimizing as it is heavily used for queue queries
                    final String compositeKey = buildCompositeCacheKey(name, attribute1, attribute2);
                    return locateFromCache(compositeKey, name, ctx);
                } else {
                    // Too many attributes are defined, don't cache it
                    return locateAndRender(name, ctx);
                }
            }
        }
    }

    private String buildCompositeCacheKey(final String name, final Entry<String, Object> attribute) {
        return name + COMPOSITE_KEY_SEPARATOR + attribute.getKey() + COMPOSITE_KEY_SEPARATOR + attribute.getValue();
    }

    private String buildCompositeCacheKey(final String name, final Entry<String, Object> attribute1, final Entry<String, Object> attribute2) {
        if (attribute1.getKey().compareTo(attribute2.getKey()) <= 0) {
            return name + COMPOSITE_KEY_SEPARATOR + attribute1.getKey() + COMPOSITE_KEY_SEPARATOR + attribute1.getValue() + COMPOSITE_KEY_SEPARATOR + attribute2.getKey() + COMPOSITE_KEY_SEPARATOR + attribute2.getValue();
        } else {
            return name + COMPOSITE_KEY_SEPARATOR + attribute2.getKey() + COMPOSITE_KEY_SEPARATOR + attribute2.getValue() + COMPOSITE_KEY_SEPARATOR + attribute1.getKey() + COMPOSITE_KEY_SEPARATOR + attribute1.getValue();
        }
    }

    private String locateFromCache(final String cacheKey, final String name, final StatementContext ctx) {
        String locatedSql = locatedSqlCache.get(cacheKey);
        if (locatedSql != null) {
            return locatedSql;
        } else {
            locatedSql = locateAndRender(name, ctx);
            // Make sure the cache is bounded in case of lots of various attributes defined (shouldn't happen in Kill Bill though)
            // Note that when defining collections for instance, you must define a collection value that is not tied to the
            // values eventually bound (e.g. query.define("record_ids", ids)), instead define a collection of generic Strings (see @BindIn and TestST4StatementLocator)
            if (locatedSqlCache.size() < 500) {
                locatedSqlCache.put(cacheKey, locatedSql);
            }
            return locatedSql;
        }
    }

    private String locateAndRender(final String name, final StatementContext ctx) {
        ST st = this.group.getInstanceOf(name);
        if (st == null) {
            // if there is no template by this name in the group, treat it as a template literal.
            st = new ST(name);
        }

        // we add all context values, ST4 explodes if you add a value that lacks a formal argument,
        // iff hasFormalArgs is true. If it is false, it just uses values opportunistically. This is gross
        // but works. -brianm
        st.impl.hasFormalArgs = false;

        st.impl.nativeGroup.errMgr = ERROR_MANAGER;

        for (final Map.Entry<String, Object> attr : ctx.getAttributes().entrySet()) {
            st.add(attr.getKey(), attr.getValue());
        }

        return st.render();
    }

    public enum UseSTGroupCache {
        YES, NO
    }

    // See https://github.com/antlr/stringtemplate4/issues/61 and https://groups.google.com/forum/#!topic/stringtemplate-discussion/4WrHlleVDFg
    private static final class STGroupFileWithThreadSafeLoading extends STGroupFile {

        public STGroupFileWithThreadSafeLoading(final URL url, final String encoding, final char delimiterStartChar, final char delimiterStopChar) {
            super(url, encoding, delimiterStartChar, delimiterStopChar);
        }

        @Override
        public CompiledST lookupTemplate(String name) {
            if (name.charAt(0) != '/') {
                name = "/" + name;
            }
            if (logger.isDebugEnabled()) {
                // getName() is expensive
                logger.debug("{}.lookupTemplate({})", getName(), name);
            }

            CompiledST code = rawGetTemplate(name);
            if (code == NOT_FOUND_ST) {
                logger.debug("{} previously seen as not found", name);
                return null;
            }

            // Try to load from disk and look up again
            if (code == null) {
                synchronized (this) {
                    code = rawGetTemplate(name);
                    if (code == null) {
                        code = load(name);
                    }
                }
            }
            if (code == null) {
                synchronized (this) {
                    code = rawGetTemplate(name);
                    if (code == null) {
                        // Note: we could do a bit better if we were to overwrite this method as well
                        code = lookupImportedTemplate(name);
                    }
                }
            }

            if (code == null) {
                logger.debug("{} recorded not found", name);
                templates.put(name, NOT_FOUND_ST);
            }
            if (code != null) {
                if (logger.isDebugEnabled()) {
                    // getName() is expensive
                    logger.debug("{}.lookupTemplate({}) found", getName(), name);
                }
            }
            return code;
        }
    }

    private static final class SLF4JSTErrorListener implements STErrorListener {

        @Override
        public void compileTimeError(final STMessage msg) {
            if (msg != null) {
                logger.warn(msg.toString());
            }
        }

        @Override
        public void runTimeError(final STMessage msg) {
            if (msg != null) {
                logger.warn(msg.toString());
            }
        }

        @Override
        public void IOError(final STMessage msg) {
            if (msg != null) {
                logger.warn(msg.toString());
            }
        }

        @Override
        public void internalError(final STMessage msg) {
            if (msg != null) {
                logger.warn(msg.toString());
            }
        }
    }
}
