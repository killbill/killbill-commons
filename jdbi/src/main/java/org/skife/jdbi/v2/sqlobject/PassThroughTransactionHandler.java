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

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionIsolationLevel;
import org.skife.jdbi.v2.TransactionStatus;

class PassThroughTransactionHandler implements Handler
{
    private final TransactionIsolationLevel isolation;

    PassThroughTransactionHandler(Method m, Transaction tx)
    {
        this.isolation = tx.value();
    }

    @Override
    public Object invoke(HandleDing ding, final Object target, final Object[] args, Callable<?> methodProxy)
    {
        ding.retain("pass-through-transaction");
        try {
            Handle h = ding.getHandle();
            if (Objects.isNull(methodProxy)) {
                throw new AbstractMethodError("Method in " + target.getClass() + " could not be invoked " +
                                              " -- it probably needs a @Sql* annotation of some kind.");
            }

            if (isolation == TransactionIsolationLevel.INVALID_LEVEL) {
                return h.inTransaction(new TransactionCallback<Object>()
                {
                    @Override
                    public Object inTransaction(Handle conn, TransactionStatus status) throws Exception
                    {
                        try {
                            return methodProxy.call();
                        }
                        catch (Throwable throwable) {
                            if (throwable instanceof Exception) {
                                throw (Exception) throwable;
                            }
                            else {
                                throw new RuntimeException(throwable);
                            }
                        }
                    }
                });
            }
            else {
                return h.inTransaction(isolation, new TransactionCallback<Object>()
                {
                    @Override
                    public Object inTransaction(Handle conn, TransactionStatus status) throws Exception
                    {
                        try {
                            return methodProxy.call();
                        }
                        catch (Throwable throwable) {
                            if (throwable instanceof Exception) {
                                throw (Exception) throwable;
                            }
                            else {
                                throw new RuntimeException(throwable);
                            }
                        }
                    }
                });

            }
        }

        finally {
            ding.release("pass-through-transaction");
        }
    }
}
