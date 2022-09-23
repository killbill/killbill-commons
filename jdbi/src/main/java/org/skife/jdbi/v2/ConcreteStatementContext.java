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

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class ConcreteStatementContext implements StatementContext {
    private final Collection<Cleanable> cleanables = new LinkedHashSet<>();
    private final Map<String, Object> attributes = new HashMap<>();

    private String rawSql;
    private String rewrittenSql;
    private String locatedSql;
    private PreparedStatement statement;
    private Connection connection;
    private Binding binding;
    private Class<?> sqlObjectType;
    private Method sqlObjectMethod;
    private boolean returningGeneratedKeys;

    ConcreteStatementContext(final Map<String, Object> globalAttributes) {
        attributes.putAll(globalAttributes);
    }

    /**
     * Specify an attribute on the statement context
     *
     * @param key   name of the attribute
     * @param value value for the attribute
     *
     * @return previous value of this attribute
     */
    @Override
    public Object setAttribute(final String key, final Object value) {
        return attributes.put(key, value);
    }

    /**
     * Obtain the value of an attribute
     *
     * @param key The name of the attribute
     *
     * @return the value of the attribute
     */
    @Override
    public Object getAttribute(final String key) {
        return this.attributes.get(key);
    }

    /**
     * Obtain all the attributes associated with this context as a map. Changes to the map
     * or to the attributes on the context will be reflected across both
     *
     * @return a map f attributes
     */
    @Override
    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }

    void setRawSql(final String rawSql) {
        this.rawSql = rawSql;
    }

    /**
     * Obtain the initial sql for the statement used to create the statement
     *
     * @return the initial sql
     */
    @Override
    public String getRawSql() {
        return rawSql;
    }

    void setLocatedSql(final String locatedSql) {
        this.locatedSql = locatedSql;
    }

    void setRewrittenSql(final String rewrittenSql) {
        this.rewrittenSql = rewrittenSql;
    }

    /**
     * Obtain the located and rewritten sql
     * <p/>
     * Not available until statement execution time
     *
     * @return the sql as it will be executed against the database
     */
    @Override
    public String getRewrittenSql()
    {
        return rewrittenSql;
    }

    /**
     * Obtain the located sql
     * <p/>
     * Not available until until statement execution time
     *
     * @return the sql which will be passed to the statement rewriter
     */
    @Override
    public String getLocatedSql() {
        return locatedSql;
    }

    void setStatement(final PreparedStatement stmt) {
        statement = stmt;
    }

    /**
     * Obtain the actual prepared statement being used.
     * <p/>
     * Not available until execution time
     *
     * @return Obtain the actual prepared statement being used.
     */
    @Override
    public PreparedStatement getStatement() {
        return statement;
    }

    void setConnection(final Connection connection) {
        this.connection = connection;
    }

    /**
     * Obtain the JDBC connection being used for this statement
     *
     * @return the JDBC connection
     */
    @Override
    public Connection getConnection() {
        return connection;
    }

    public void setBinding(final Binding b) {
        this.binding = b;
    }

    @Override
    public Binding getBinding() {
        return binding;
    }

    public void setSqlObjectType(final Class<?> sqlObjectType) {
        this.sqlObjectType = sqlObjectType;
    }

    @Override
    public Class<?> getSqlObjectType() {
        return sqlObjectType;
    }

    public void setSqlObjectMethod(final Method sqlObjectMethod) {
        this.sqlObjectMethod = sqlObjectMethod;
    }

    @Override
    public Method getSqlObjectMethod() {
        return sqlObjectMethod;
    }

    public void setReturningGeneratedKeys(final boolean b) {
        this.returningGeneratedKeys = b;
    }

    @Override
    public boolean isReturningGeneratedKeys() {
        return returningGeneratedKeys;
    }

    @Override
    public void addCleanable(final Cleanable cleanable) {
        this.cleanables.add(cleanable);
    }

    @Override
    public Collection<Cleanable> getCleanables() {
        return cleanables;
    }
}
