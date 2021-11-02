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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.skife.jdbi.v2.GeneratedKeys;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.PreparedBatch;
import org.skife.jdbi.v2.PreparedBatchPart;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.exceptions.DBIException;
import org.skife.jdbi.v2.exceptions.UnableToCreateStatementException;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.skife.jdbi.v2.sqlobject.customizers.BatchChunkSize;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.fasterxml.classmate.members.ResolvedMethod;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings("IT_NO_SUCH_ELEMENT")
class BatchHandler extends CustomizingStatementHandler
{
    private final String  sql;
    private final boolean transactional;
    private final ChunkSizeFunction batchChunkSize;
    private final Returner returner;

    BatchHandler(Class<?> sqlObjectType, ResolvedMethod method) {
        super(sqlObjectType, method);

        Method raw_method = method.getRawMember();
        SqlBatch anno = raw_method.getAnnotation(SqlBatch.class);
        this.sql = SqlObject.getSql(anno, raw_method);
        this.transactional = anno.transactional();
        this.batchChunkSize = determineBatchChunkSize(sqlObjectType, raw_method);
        final GetGeneratedKeys getGeneratedKeys = raw_method.getAnnotation(GetGeneratedKeys.class);
        if (getGeneratedKeys == null) {
            if (!returnTypeIsValid(method.getRawMember().getReturnType())) {
                throw new DBIException(invalidReturnTypeMessage(method)) {};
            }
            returner = new Returner() {
                @Override
                public Object value(PreparedBatch batch, HandleDing baton)
                {
                    return batch.execute();
                }
            };
        } else {
            final ResultSetMapper mapper;
            try {
                mapper = getGeneratedKeys.value().newInstance();
            } catch (Exception e) {
                throw new UnableToCreateStatementException("Unable to instantiate result set mapper for statement", e);
            }
            final ResultReturnThing magic = ResultReturnThing.forType(method);
            if (getGeneratedKeys.columnName().isEmpty()) {
                returner = new Returner() {
                    @Override
                    public Object value(PreparedBatch batch, HandleDing baton)
                    {
                        GeneratedKeys o = batch.executeAndGenerateKeys(mapper);
                        return magic.result(o, baton);
                    }
                };
            } else {
                returner = new Returner() {
                    @Override
                    public Object value(PreparedBatch batch, HandleDing baton)
                    {
                        String columnName = getGeneratedKeys.columnName();
                        GeneratedKeys o = batch.executeAndGenerateKeys(mapper, columnName);
                        return magic.result(o, baton);
                    }
                };
            }
        }
    }

    private ChunkSizeFunction determineBatchChunkSize(Class<?> sqlObjectType, Method raw_method)
    {
        // this next big if chain determines the batch chunk size. It looks from most specific
        // scope to least, that is: as an argument, then on the method, then on the class,
        // then default to Integer.MAX_VALUE

        int index_of_batch_chunk_size_annotation_on_parameter;
        if ((index_of_batch_chunk_size_annotation_on_parameter = findBatchChunkSizeFromParam(raw_method)) >= 0) {
            return new ParamBasedChunkSizeFunction(index_of_batch_chunk_size_annotation_on_parameter);
        }
        else if (raw_method.isAnnotationPresent(BatchChunkSize.class)) {
            final int size = raw_method.getAnnotation(BatchChunkSize.class).value();
            if (size <= 0) {
                throw new IllegalArgumentException("Batch chunk size must be >= 0");
            }
            return new ConstantChunkSizeFunction(size);
        }
        else if (sqlObjectType.isAnnotationPresent(BatchChunkSize.class)) {
            final int size = BatchChunkSize.class.cast(sqlObjectType.getAnnotation(BatchChunkSize.class)).value();
            return new ConstantChunkSizeFunction(size);
        }
        else {
            return new ConstantChunkSizeFunction(Integer.MAX_VALUE);
        }
    }

    private int findBatchChunkSizeFromParam(Method raw_method)
    {
        Annotation[][] param_annos = raw_method.getParameterAnnotations();
        for (int i = 0; i < param_annos.length; i++) {
            Annotation[] annos = param_annos[i];
            for (Annotation anno : annos) {
                if (anno.annotationType().isAssignableFrom(BatchChunkSize.class)) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public Object invoke(HandleDing h, Object target, Object[] args)
    {
        boolean foundIterator = false;
        Handle handle = h.getHandle();

        List<Iterator> extras = new ArrayList<Iterator>();
        for (final Object arg : args) {
            if (arg instanceof Iterable) {
                extras.add(((Iterable) arg).iterator());
                foundIterator = true;
            }
            else if (arg instanceof Iterator) {
                extras.add((Iterator) arg);
                foundIterator = true;
            }
            else if (arg.getClass().isArray()) {
                extras.add(Arrays.asList((Object[])arg).iterator());
                foundIterator = true;
            }
            else {
                extras.add(new Iterator() {
                               @Override
                               public boolean hasNext() {
                                   return true;
                               }

                               @Override
                               public Object next() {
                                   // This is the table name for instance (repeated during iteration)
                                   return arg;
                               }

                               @Override
                               public void remove() {
                                   // NOOP
                               }
                           }
                          );
            }
        }

        if (!foundIterator) {
            throw new UnableToExecuteStatementException("@SqlBatch must have at least one iterable parameter", (StatementContext)null);
        }

        int processed = 0;
        List<Object> results = new LinkedList<Object>();

        PreparedBatch batch = handle.prepareBatch(sql);
        applyCustomizers(batch, args);
        Object[] _args;
        int chunk_size = batchChunkSize.call(args);

        while ((_args = next(extras)) != null) {
            PreparedBatchPart part = batch.add();
            applyBinders(part, _args);

            if (++processed == chunk_size) {
                // execute this chunk
                processed = 0;
                executeBatch(results, h, handle, batch);
                batch = handle.prepareBatch(sql);
                applyCustomizers(batch, args);
            }
        }

        //execute the rest
        if (batch.getSize() > 0) {
            executeBatch(results, h, handle, batch);
        }

        return results;
    }

    private void executeBatch(final Collection<Object> results, final HandleDing h, final Handle handle, final PreparedBatch batch) {
        final Object res = executeBatch(h, handle, batch);
        if (res instanceof Collection) {
            results.addAll((Collection) res);
        } else {
            results.add(res);
        }
    }

    private Object executeBatch(final HandleDing h, final Handle handle, final PreparedBatch batch)
    {
        if (!handle.isInTransaction() && transactional) {
            // it is safe to use same prepared batch as the inTransaction passes in the same
            // Handle instance.
            return handle.inTransaction(new TransactionCallback<Object>()
            {
                @Override
                public Object inTransaction(Handle conn, TransactionStatus status) throws Exception
                {
                    return returner.value(batch, h);
                }
            });
        }
        else {
            return returner.value(batch, h);
        }
    }

    private static Object[] next(List<Iterator> args)
    {
        List<Object> rs = new ArrayList<Object>();
        for (Iterator arg : args) {
            if (arg.hasNext()) {
                rs.add(arg.next());
            }
            else {
                return null;
            }
        }
        return rs.toArray();
    }

    private interface Returner
    {
        Object value(PreparedBatch batch, HandleDing baton);
    }

    private interface ChunkSizeFunction
    {
        int call(Object[] args);
    }

    private static class ConstantChunkSizeFunction implements ChunkSizeFunction
    {
        private final int value;

        ConstantChunkSizeFunction(int value) {
            this.value = value;
        }

        @Override
        public int call(Object[] args)
        {
            return value;
        }
    }

    private static class ParamBasedChunkSizeFunction implements ChunkSizeFunction
    {
        private final int index;

        ParamBasedChunkSizeFunction(int index) {
            this.index = index;
        }

        @Override
        public int call(Object[] args)
        {
            return (Integer)args[index];
        }
    }

    private static boolean returnTypeIsValid(Class<?> type) {
        if (type.equals(Void.TYPE) || type.isArray() && type.getComponentType().equals(Integer.TYPE)) {
            return true;
        }

        return false;
    }

    private static String invalidReturnTypeMessage(ResolvedMethod method) {
        return method.getDeclaringType() + "." + method +
               " method is annotated with @SqlBatch so should return void or int[] but is returning: " +
               method.getReturnType();
    }
}
