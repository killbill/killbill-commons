/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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
import java.util.List;

import org.killbill.queue.dao.QueueSqlDao;
import org.killbill.queue.dao.QueueSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.Define;

@QueueSqlDaoStringTemplate
public interface NotificationSqlDao extends QueueSqlDao<NotificationEventModelDao> {

    @SqlQuery
    public List<NotificationEventModelDao> getReadyQueueEntriesForSearchKeys(@Bind("queueName") String queueName,
                                                                             @Bind("searchKey1") final Long searchKey1,
                                                                             @Bind("searchKey2") final Long searchKey2,
                                                                             @Define("tableName") final String tableName);

    @SqlQuery
    public List<NotificationEventModelDao> getReadyQueueEntriesForSearchKey2(@Bind("queueName") String queueName,
                                                                             @Bind("searchKey2") final Long searchKey2,
                                                                             @Define("tableName") final String tableName);

    @SqlQuery
    public List<NotificationEventModelDao> getReadyOrInProcessingQueueEntriesForSearchKeys(@Bind("queueName") String queueName,
                                                                                           @Bind("searchKey1") final Long searchKey1,
                                                                                           @Bind("searchKey2") final Long searchKey2,
                                                                                           @Define("tableName") final String tableName);

    @SqlQuery
    public List<NotificationEventModelDao> getReadyOrInProcessingQueueEntriesForSearchKey2(@Bind("queueName") String queueName,
                                                                                           @Bind("searchKey2") final Long searchKey2,
                                                                                           @Define("tableName") final String tableName);


    @SqlQuery
    public int getCountReadyEntries(@Bind("searchKey1") final Long searchKey1,
                                    @Bind("searchKey2") final Long searchKey2,
                                    @Bind("now") Date now,
                                    @Define("tableName") final String tableName);
}
