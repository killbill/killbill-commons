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

package org.killbill.notificationq.dao;

import java.util.Date;
import java.util.Iterator;

import org.joda.time.DateTime;
import org.killbill.commons.jdbi.statement.SmartFetchSize;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.killbill.queue.dao.QueueSqlDao;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.Define;

@KillBillSqlDaoStringTemplate
public interface NotificationSqlDao extends QueueSqlDao<NotificationEventModelDao> {

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    Iterator<NotificationEventModelDao> getReadyQueueEntriesForSearchKeys(@Bind("queueName") String queueName,
                                                                          @Bind("searchKey1") final Long searchKey1,
                                                                          @Bind("searchKey2") final Long searchKey2,
                                                                          @Define("tableName") final String tableName);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    Iterator<NotificationEventModelDao> getReadyQueueEntriesForSearchKey2(@Bind("queueName") String queueName,
                                                                          @Bind("maxEffectiveDate") final DateTime maxEffectiveDate,
                                                                          @Bind("searchKey2") final Long searchKey2,
                                                                          @Define("tableName") final String tableName);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    Iterator<NotificationEventModelDao> getReadyOrInProcessingQueueEntriesForSearchKeys(@Bind("queueName") String queueName,
                                                                                        @Bind("searchKey1") final Long searchKey1,
                                                                                        @Bind("searchKey2") final Long searchKey2,
                                                                                        @Define("tableName") final String tableName);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    Iterator<NotificationEventModelDao> getReadyOrInProcessingQueueEntriesForSearchKey2(@Bind("queueName") String queueName,
                                                                                        @Bind("maxEffectiveDate") final DateTime maxEffectiveDate,
                                                                                        @Bind("searchKey2") final Long searchKey2,
                                                                                        @Define("tableName") final String tableName);


    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    Iterator<NotificationEventModelDao> getHistoricalQueueEntriesForSearchKeys(@Bind("queueName") String queueName,
                                                                               @Bind("searchKey1") final Long searchKey1,
                                                                               @Bind("searchKey2") final Long searchKey2,
                                                                               @Define("historyTableName") final String historyTableName);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    Iterator<NotificationEventModelDao> getHistoricalQueueEntriesForSearchKey2(@Bind("queueName") String queueName,
                                                                               @Bind("minEffectiveDate") final DateTime minEffectiveDate,
                                                                               @Bind("searchKey2") final Long searchKey2,
                                                                               @Define("historyTableName") final String historyTableName);


    @SqlQuery
    int getCountReadyEntries(@Bind("searchKey1") final Long searchKey1,
                             @Bind("searchKey2") final Long searchKey2,
                             @Bind("now") Date now,
                             @Define("tableName") final String tableName);
}
