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

package com.ning.billing.notificationq.dao;

import com.ning.billing.queue.dao.QueueSqlDao;
import com.ning.billing.queue.dao.QueueSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.Define;

import java.util.List;

@QueueSqlDaoStringTemplate
public interface NotificationSqlDao extends QueueSqlDao<NotificationEventModelDao> {

    @SqlQuery
    public List<NotificationEventModelDao> getReadyQueueEntriesForSearchKey(@Bind("queueName") String queueName,
                                                                            @Bind("searchKey") final Long keyValue,
                                                                            @Define("tableName") final String tableName,
                                                                            @Define("searchKey") final String searchKey);

    @SqlQuery
    public int getCountReadyEntries(@Bind("searchKey") final Long keyValue,
                                    @Define("tableName") final String tableName,
                                    @Define("searchKey") final String searchKey);


}
