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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.stringtemplate.UseStringTemplate3StatementLocator;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.commons.jdbi.binder.BinderBase;
import com.ning.billing.commons.jdbi.mapper.MapperBase;
import com.ning.billing.queue.api.EventEntry.PersistentQueueEntryLifecycleState;

@UseStringTemplate3StatementLocator()
public interface NotificationSqlDao extends Transactional<NotificationSqlDao>, CloseMe {

    @SqlQuery
    @Mapper(NotificationSqlMapper.class)
    public List<NotificationEventEntry> getReadyNotifications(@Bind("now") Date now,
                                                    @Bind("owner") String owner,
                                                    @Bind("max") int max);

    @SqlQuery
    public int getPendingCountNotifications(@Bind("now") Date now);

    @SqlQuery
    @Mapper(NotificationSqlMapper.class)
    public List<NotificationEventEntry> getFutureNotificationsForAccount(@Bind("now") Date now,
                                                               @Bind("queueName") String queueName,
                                                               @Bind("searchKey1") long searchKey1);

    @SqlUpdate
    public void removeNotification(@Bind("record") Long id);

    @SqlUpdate
    public int claimNotification(@Bind("owner") String owner,
                                 @Bind("nextAvailable") Date nextAvailable,
                                 @Bind("recordId") Long recordId,
                                 @Bind("now") Date now);

    @SqlUpdate
    public void clearNotification(@Bind("recordId") Long recordId,
                                  @Bind("owner") String owner);

    @SqlUpdate
    public void insertNotification(@Bind(binder = NotificationSqlDaoBinder.class) NotificationEventEntry evt);

    @SqlUpdate
    public void insertClaimedHistory(@Bind("ownerId") String ownerId,
                                     @Bind("claimedDate") Date claimedDate,
                                     @Bind("notificationRecordId") Long notificationRecordId,
                                     @Bind("searchKey1") long searchKey1,
                                     @Bind("searchKey2") long searchKey2);

    public static class NotificationSqlDaoBinder extends BinderBase implements Binder<Bind, NotificationEventEntry> {

        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final NotificationEventEntry evt) {
            stmt.bind("createdDate", getDate(new DateTime()));
            stmt.bind("creatingOwner", evt.getCreatedOwner());
            stmt.bind("className", evt.getEventClass());
            stmt.bind("futureUserToken", getUUIDString(evt.getFutureUserToken()));
            stmt.bind("eventJson", evt.getEventJson());
            stmt.bind("effectiveDate", getDate(evt.getEffectiveDate()));
            stmt.bind("queueName", evt.getQueueName());
            stmt.bind("processingAvailableDate", getDate(evt.getNextAvailableDate()));
            stmt.bind("processingOwner", evt.getOwner());
            stmt.bind("processingState", PersistentQueueEntryLifecycleState.AVAILABLE.toString());
            stmt.bind("userToken", getUUIDString(evt.getUserToken()));
            stmt.bind("searchKey1", evt.getSearchKey1());
            stmt.bind("searchKey2", evt.getSearchKey2());
        }
    }

    public static class NotificationSqlMapper extends MapperBase implements ResultSetMapper<NotificationEventEntry> {

        @Override
        public NotificationEventEntry map(final int index, final ResultSet r, final StatementContext ctx)
                throws SQLException {

            final Long recordId = r.getLong("record_id");
            final String createdOwner = r.getString("creating_owner");
            final String className = r.getString("class_name");
            final String eventJson = r.getString("event_json");
            final UUID userToken = getUUID(r, "user_token");
            final DateTime nextAvailableDate = getDateTime(r, "processing_available_date");
            final String processingOwner = r.getString("processing_owner");
            final PersistentQueueEntryLifecycleState processingState = PersistentQueueEntryLifecycleState.valueOf(r.getString("processing_state"));
            final Long searchKey1 = r.getLong("search_key1");
            final Long searchKey2 = r.getLong("search_key2");
            final UUID futureUserToken = getUUID(r, "future_user_token");
            final String queueName = r.getString("queue_name");
            final DateTime effectiveDate = getDateTime(r, "effective_date");
            return new NotificationEventEntry(recordId, createdOwner, processingOwner, nextAvailableDate, processingState, className, eventJson, userToken, searchKey1, searchKey2, futureUserToken, effectiveDate, queueName);
        }
    }
}
