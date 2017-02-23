/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.bus.dao;

import java.util.Iterator;
import java.util.List;

import org.joda.time.DateTime;
import org.killbill.commons.jdbi.statement.SmartFetchSize;
import org.killbill.queue.dao.QueueSqlDao;
import org.killbill.queue.dao.QueueSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.Define;

@QueueSqlDaoStringTemplate
public interface PersistentBusSqlDao extends QueueSqlDao<BusEventModelDao> {

    @SqlQuery
    public List<BusEventModelDao> getReadyQueueEntriesForSearchKeys(@Bind("searchKey1") final Long searchKey1,
                                                                    @Bind("searchKey2") final Long searchKey2,
                                                                    @Define("tableName") final String tableName);

    @SqlQuery
    public List<BusEventModelDao> getReadyQueueEntriesForSearchKey2(@Bind("searchKey2") final Long searchKey2,
                                                                    @Define("tableName") final String tableName);

    @SqlQuery
    public List<BusEventModelDao> getReadyOrInProcessingQueueEntriesForSearchKeys(@Bind("searchKey1") final Long searchKey1,
                                                                                  @Bind("searchKey2") final Long searchKey2,
                                                                                  @Define("tableName") final String tableName);

    @SqlQuery
    public List<BusEventModelDao> getReadyOrInProcessingQueueEntriesForSearchKey2(@Bind("searchKey2") final Long searchKey2,
                                                                                  @Define("tableName") final String tableName);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<BusEventModelDao> getHistoricalQueueEntriesForSearchKeys(@Bind("searchKey1") final Long searchKey1,
                                                                             @Bind("searchKey2") final Long searchKey2,
                                                                             @Define("historyTableName") final String historyTableName);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<BusEventModelDao> getHistoricalQueueEntriesForSearchKey2(@Bind("minCreatedDate") final DateTime minCreatedDate,
                                                                             @Bind("searchKey2") final Long searchKey2,
                                                                             @Define("historyTableName") final String historyTableName);
}
