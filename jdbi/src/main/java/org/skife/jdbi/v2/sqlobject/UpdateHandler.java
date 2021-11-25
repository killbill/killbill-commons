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
package org.skife.jdbi.v2.sqlobject;

import java.util.concurrent.Callable;

import org.skife.jdbi.v2.ConcreteStatementContext;
import org.skife.jdbi.v2.GeneratedKeys;
import org.skife.jdbi.v2.Update;
import org.skife.jdbi.v2.exceptions.UnableToCreateStatementException;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.fasterxml.classmate.members.ResolvedMethod;

class UpdateHandler extends CustomizingStatementHandler
{
    private final String sql;
    private final Returner returner;

    public UpdateHandler(Class<?> sqlObjectType, ResolvedMethod method)
    {
        super(sqlObjectType, method);
        this.sql = SqlObject.getSql(method.getRawMember().getAnnotation(SqlUpdate.class), method.getRawMember());
        if (method.getRawMember().isAnnotationPresent(GetGeneratedKeys.class)) {

            final ResultReturnThing magic = ResultReturnThing.forType(method);
            final GetGeneratedKeys ggk = method.getRawMember().getAnnotation(GetGeneratedKeys.class);
            final ResultSetMapper mapper;
            try {
                mapper = ggk.value().newInstance();
            }
            catch (Exception e) {
                throw new UnableToCreateStatementException("Unable to instantiate result set mapper for statement", e);
            }
            if (ggk.columnName().isEmpty()) {
                returner = new Returner() {
                    @Override
                    public Object value(Update update, HandleDing baton)
                    {
                        GeneratedKeys o = update.executeAndReturnGeneratedKeys(mapper);
                        return magic.result(o, baton);
                    }
                };
            } else {
                returner = new Returner() {
                    @Override
                    public Object value(Update update, HandleDing baton)
                    {
                        String columnName = ggk.columnName();
                        GeneratedKeys o = update.executeAndReturnGeneratedKeys(mapper, columnName);
                        return magic.result(o, baton);
                    }
                };
            }
        }
        else {
            this.returner = new Returner()
            {
                @Override
                public Object value(Update update, HandleDing baton)
                {
                    return update.execute();
                }
            };
        }
    }

    @Override
    public Object invoke(HandleDing h, Object target, Object[] args, Callable<?> methodProxy)
    {
        Update q = h.getHandle().createStatement(sql);
        populateSqlObjectData((ConcreteStatementContext)q.getContext());
        applyCustomizers(q, args);
        applyBinders(q, args);
        return this.returner.value(q, h);
    }


    private interface Returner
    {
        Object value(Update update, HandleDing baton);
    }
}
