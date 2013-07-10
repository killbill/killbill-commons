/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.queue.dao;

import java.util.Date;
import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;

@QueueSqlDaoStringTemplate
public interface QueueSqlDao<T extends EventEntryModelDao> extends Transactional<QueueSqlDao<T>>, CloseMe {

    @SqlQuery
    public Long getLastInsertId();


    @SqlQuery
    public T getByRecordId(@Bind("recordId") Long id,
                           @Define("tableName") final String tableName);

    @SqlQuery
    public List<T> getEntriesFromIds(@RecordIdCollectionBinder final List<Long> recordIds,
                                     @Define("tableName") final String tableName);

    @SqlQuery
    public List<T> getReadyEntries(@Bind("now") Date now,
                                   @Bind("owner") String owner,
                                   @Bind("max") int max,
                                   @Define("tableName") final String tableName);

    @SqlUpdate
    public int claimEntry(@Bind("recordId") Long id,
                          @Bind("now") Date now,
                          @Bind("owner") String owner,
                          @Bind("nextAvailable") Date nextAvailable,
                          @Define("tableName") final String tableName);

    @SqlUpdate
    public void removeEntry(@Bind("recordId") Long id,
                            @Define("tableName") final String tableName);

    @SqlUpdate
    public void insertEntry(@BindBean T evt,
                            @Define("tableName") final String tableName);


}
