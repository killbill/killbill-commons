/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.queue.dao;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;

import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.BatchChunkSize;
import org.skife.jdbi.v2.sqlobject.customizers.Define;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.unstable.BindIn;
import org.skife.jdbi.v2.util.LongMapper;

@KillBillSqlDaoStringTemplate
public interface QueueSqlDao<T extends EventEntryModelDao> extends Transactional<QueueSqlDao<T>>, CloseMe {

    @SqlQuery
    Long getMaxRecordId(@Define("tableName") final String tableName);

    @SqlQuery
    T getByRecordId(@Bind("recordId") Long id,
                    @Define("tableName") final String tableName);

    @SqlQuery
    List<T> getEntriesFromIds(@BindIn("record_ids") final List<Long> recordIds,
                              @Define("tableName") final String tableName);

    @SqlQuery
    List<T> getReadyEntries(@Bind("now") Date now,
                            @Bind("max") int max,
                            // This is somewhat a hack, should really be a @Bind parameter but we also use it
                            // for StringTemplate to modify the query based whether value is null or not.
                            @Nullable @Define("owner") String owner,
                            @Define("tableName") final String tableName);


    @SqlQuery
    long getNbReadyEntries(@Bind("now") Date now,
                            // This is somewhat a hack, should really be a @Bind parameter but we also use it
                            // for StringTemplate to modify the query based whether value is null or not.
                            @Nullable @Define("owner") String owner,
                            @Define("tableName") final String tableName);

    @SqlQuery
    List<T> getInProcessingEntries(@Define("tableName") final String tableName);

    @SqlQuery
    List<T> getEntriesLeftBehind(@Bind("max") int max,
                                 @Bind("now") Date now,
                                 @Bind("reapingDate") Date reapingDate,
                                 @Define("tableName") final String tableName);

    @SqlUpdate
    int claimEntry(@Bind("recordId") Long id,
                   @Bind("owner") String owner,
                   @Bind("nextAvailable") Date nextAvailable,
                   @Define("tableName") final String tableName);

    @SqlUpdate
    int claimEntries(@BindIn("record_ids") final Collection<Long> recordIds,
                     @Bind("owner") String owner,
                     @Bind("nextAvailable") Date nextAvailable,
                     @Define("tableName") final String tableName);

    @SqlUpdate
    int updateOnError(@Bind("recordId") Long id,
                      @Bind("now") Date now,
                      @Bind("errorCount") Long errorCount,
                      @Define("tableName") final String tableName);

    @SqlUpdate
    void removeEntry(@Bind("recordId") Long id,
                     @Define("tableName") final String tableName);

    @SqlUpdate
    void removeEntries(@BindIn("record_ids") final Collection<Long> recordIds,
                       @Define("tableName") final String tableName);

    @SqlUpdate
    @GetGeneratedKeys(value = LongMapper.class, columnName = "record_id")
    Long insertEntry(@SmartBindBean T evt,
                     @Define("tableName") final String tableName);

    @SqlBatch
    @BatchChunkSize(100)
    void insertEntries(@SmartBindBean Iterable<T> evts,
                       @Define("tableName") final String tableName);
}
