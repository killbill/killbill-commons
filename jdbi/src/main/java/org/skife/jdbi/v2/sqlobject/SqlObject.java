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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.classmate.MemberResolver;
import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.ResolvedTypeWithMembers;
import com.fasterxml.classmate.TypeResolver;
import com.fasterxml.classmate.members.ResolvedMethod;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.TypeCache;
import net.bytebuddy.TypeCache.WithInlineExpunction;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default;
import net.bytebuddy.implementation.MethodDelegation;

import static net.bytebuddy.matcher.ElementMatchers.any;

class SqlObject
{
    private static final TypeResolver typeResolver  = new TypeResolver();
    private static final Map<Method, Handler> mixinHandlers = new HashMap<>();
    private static final ConcurrentMap<Class<?>, Map<Method, Handler>> handlersCache = new ConcurrentHashMap<>();
    private static final TypeCache<Class<?>> typeCache = new WithInlineExpunction<>(TypeCache.Sort.SOFT);

    static {
        mixinHandlers.putAll(TransactionalHelper.handlers());
        mixinHandlers.putAll(GetHandleHelper.handlers());
        mixinHandlers.putAll(TransmogrifierHelper.handlers());
    }

    @SuppressWarnings("unchecked")
    static <T> T buildSqlObject(final Class<T> sqlObjectType, final HandleDing handle)
    {
        final SqlObject so = new SqlObject(buildHandlersFor(sqlObjectType), handle);

        Class<?> loadedClass = typeCache.findOrInsert(sqlObjectType.getClassLoader(), sqlObjectType, () -> new ByteBuddy()
                .subclass(sqlObjectType)
                .implement(CloseInternalDoNotUseThisClass.class)
                .method(any())
                .intercept(MethodDelegation.to(new SqlObjectInterceptor(so)))
                .make()
                .load(sqlObjectType.getClassLoader(), Default.INJECTION)
                .getLoaded());

        try {
            return sqlObjectType.cast(loadedClass.getConstructor().newInstance());
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError("Failed to instantiate proxy class for " + sqlObjectType.getName(), e);
        }
    }

    private static Map<Method, Handler> buildHandlersFor(Class<?> sqlObjectType)
    {
        if (handlersCache.containsKey(sqlObjectType)) {
            return handlersCache.get(sqlObjectType);
        }

        final MemberResolver mr = new MemberResolver(typeResolver);
        final ResolvedType sql_object_type = typeResolver.resolve(sqlObjectType);

        final ResolvedTypeWithMembers d = mr.resolve(sql_object_type, null, null);

        final Map<Method, Handler> handlers = new HashMap<Method, Handler>();
        for (final ResolvedMethod method : d.getMemberMethods()) {
            final Method raw_method = method.getRawMember();

            if (raw_method.isAnnotationPresent(SqlQuery.class)) {
                handlers.put(raw_method, new QueryHandler(sqlObjectType, method, ResultReturnThing.forType(method)));
            }
            else if (raw_method.isAnnotationPresent(SqlUpdate.class)) {
                handlers.put(raw_method, new UpdateHandler(sqlObjectType, method));
            }
            else if (raw_method.isAnnotationPresent(SqlBatch.class)) {
                handlers.put(raw_method, new BatchHandler(sqlObjectType, method));
            }
            else if (raw_method.isAnnotationPresent(SqlCall.class)) {
                handlers.put(raw_method, new CallHandler(sqlObjectType, method));
            }
            else if(raw_method.isAnnotationPresent(CreateSqlObject.class)) {
                handlers.put(raw_method, new CreateSqlObjectHandler(raw_method.getReturnType()));
            }
            else if (method.getName().equals("close") && method.getRawMember().getParameterTypes().length == 0) {
                handlers.put(raw_method, new CloseHandler());
            }
            else if (raw_method.isAnnotationPresent(Transaction.class)) {
                handlers.put(raw_method, new PassThroughTransactionHandler(raw_method, raw_method.getAnnotation(Transaction.class)));
            }
            else if (mixinHandlers.containsKey(raw_method)) {
                handlers.put(raw_method, mixinHandlers.get(raw_method));
            }
            else {
                handlers.put(raw_method, new PassThroughHandler(raw_method));
            }
        }

        // this is an implicit mixin, not an explicit one, so we need to *always* add it
        handlers.putAll(CloseInternalDoNotUseThisClass.Helper.handlers());

        handlers.putAll(EqualsHandler.handler());
        handlers.putAll(ToStringHandler.handler(sqlObjectType.getName()));
        handlers.putAll(HashCodeHandler.handler());

        handlersCache.put(sqlObjectType, handlers);

        return handlers;
    }


    private final Map<Method, Handler> handlers;
    private final HandleDing           ding;

    private static final AtomicLong RETAINER = new AtomicLong();

    public SqlObject(Map<Method, Handler> handlers, HandleDing ding)
    {
        this.handlers = handlers;
        this.ding = ding;
    }

    public Object invoke(Object proxy, Method method, Object[] args, Callable<?> methodProxy) throws Throwable
    {
        Handler handler = handlers.get(method);

        // If there is no handler, pretend we are just an Object and don't open a connection (Issue #82)
        if (handler == null || handler instanceof PassThroughHandler) {
            if (Objects.isNull(methodProxy)) {
                throw new AbstractMethodError("Method " + method.getDeclaringClass().getName() + "#" + method.getName() +
                                              " doesn't make sense -- it probably needs a @Sql* annotation of some kind.");
            }

            return methodProxy.call();
        }

        Throwable doNotMask = null;
        // [OPTIMIZATION] method.toString() is expensive
        final String retainName = String.valueOf(RETAINER.getAndIncrement());
        try {
            ding.retain(retainName);
            return handler.invoke(ding, proxy, args, methodProxy);
        }
        catch (Throwable e) {
            doNotMask = e;
            throw e;
        }
        finally {
            try {
                ding.release(retainName);
            }
            catch (Throwable e) {
                if (doNotMask==null) {
                    throw e;
                }
            }
        }
    }

    public static void close(Object sqlObject)
    {
        if (!(sqlObject instanceof CloseInternalDoNotUseThisClass)) {
            throw new IllegalArgumentException(sqlObject + " is not a sql object");
        }
        CloseInternalDoNotUseThisClass closer = (CloseInternalDoNotUseThisClass) sqlObject;
        closer.___jdbi_close___();
    }

    static String getSql(SqlCall q, Method m)
    {
        if (SqlQuery.DEFAULT_VALUE.equals(q.value())) {
            return m.getName();
        }
        else {
            return q.value();
        }
    }

    static String getSql(SqlQuery q, Method m)
    {
        if (SqlQuery.DEFAULT_VALUE.equals(q.value())) {
            return m.getName();
        }
        else {
            return q.value();
        }
    }

    static String getSql(SqlUpdate q, Method m)
    {
        if (SqlQuery.DEFAULT_VALUE.equals(q.value())) {
            return m.getName();
        }
        else {
            return q.value();
        }
    }

    static String getSql(SqlBatch q, Method m)
    {
        if (SqlQuery.DEFAULT_VALUE.equals(q.value())) {
            return m.getName();
        }
        else {
            return q.value();
        }
    }
}
