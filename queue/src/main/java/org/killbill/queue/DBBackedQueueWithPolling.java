/*
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

package org.killbill.queue;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.clock.Clock;
import org.killbill.queue.api.PersistentQueueConfig;
import org.killbill.queue.api.PersistentQueueConfig.PersistentQueueMode;
import org.killbill.queue.api.PersistentQueueEntryLifecycleState;
import org.killbill.queue.dao.EventEntryModelDao;
import org.killbill.queue.dao.QueueSqlDao;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class DBBackedQueueWithPolling<T extends EventEntryModelDao> extends DBBackedQueue<T> {

    public DBBackedQueueWithPolling(final Clock clock,
                                    final IDBI dbi,
                                    final Class<? extends QueueSqlDao<T>> sqlDaoClass,
                                    final PersistentQueueConfig config,
                                    final String dbBackedQId,
                                    final MetricRegistry metricRegistry) {
        super(clock, dbi, sqlDaoClass, config, dbBackedQId, metricRegistry);
    }

    @Override
    public void initialize() {
        log.info("{} Initialized  mode={}",
                 DB_QUEUE_LOG_ID, config.getPersistentQueueMode());
    }

    @Override
    public void close() {
    }


    @Override
    public void insertEntryFromTransaction(final QueueSqlDao<T> transactional, final T entry) {
        safeInsertEntry(transactional, entry);
    }

    @Override
    public ReadyEntriesWithMetrics<T> getReadyEntries() {
        final long ini = System.nanoTime();
        final List<T> claimedEntries = executeTransaction(new Transaction<List<T>, QueueSqlDao<T>>() {
            @Override
            public List<T> inTransaction(final QueueSqlDao<T> queueSqlDao, final TransactionStatus status) throws Exception {
                final DateTime now = clock.getUTCNow();

                final List<T> entriesToClaim = fetchReadyEntries(now, config.getMaxEntriesClaimed(), queueSqlDao);

                List<T> claimedEntries = ImmutableList.of();
                if (!entriesToClaim.isEmpty()) {
                    log.debug("{} Entries to claim: {}", DB_QUEUE_LOG_ID, entriesToClaim);
                    claimedEntries = claimEntries(now, entriesToClaim, queueSqlDao);
                }

                return claimedEntries;
            }
        });
        return new ReadyEntriesWithMetrics<T>(claimedEntries, System.nanoTime() - ini);
    }

    @Override
    public void updateOnError(final T entry) {
        executeTransaction(new Transaction<Void, QueueSqlDao<T>>() {
            @Override
            public Void inTransaction(final QueueSqlDao<T> transactional, final TransactionStatus status) throws Exception {
                transactional.updateOnError(entry.getRecordId(), clock.getUTCNow().toDate(), entry.getErrorCount(), config.getTableName());
                return null;
            }
        });
    }

    @Override
    protected void insertReapedEntriesFromTransaction(final QueueSqlDao<T> transactional, final List<T> entriesLeftBehind, final DateTime now) {
        for (final T entry : entriesLeftBehind) {
            entry.setCreatedDate(now);
            entry.setProcessingState(PersistentQueueEntryLifecycleState.AVAILABLE);
            entry.setCreatingOwner(CreatorName.get());
            entry.setProcessingOwner(null);
        }
        transactional.insertEntries(entriesLeftBehind, config.getTableName());
    }

    private List<T> fetchReadyEntries(final DateTime now, final int maxEntries, final QueueSqlDao<T> queueSqlDao) {
        final String owner = config.getPersistentQueueMode() == PersistentQueueMode.POLLING ? null : CreatorName.get();
        final long ini = System.nanoTime();
        final List<T> result = queueSqlDao.getReadyEntries(now.toDate(), maxEntries, owner, config.getTableName());
        rawGetEntriesTime.update(System.nanoTime() - ini, TimeUnit.NANOSECONDS);
        return result;
    }

    private List<T> claimEntries(final DateTime now, final List<T> candidates, final QueueSqlDao<T> queueSqlDao) {
        switch (config.getPersistentQueueMode()) {
            case POLLING:
                return sequentialClaimEntries(now, candidates, queueSqlDao);

            case STICKY_POLLING:
                return batchClaimEntries(now, candidates, queueSqlDao);

            default:
                throw new IllegalStateException("Unsupported PersistentQueueMode " + config.getPersistentQueueMode());
        }
    }

    private List<T> batchClaimEntries(final DateTime utcNow, final List<T> candidates, final QueueSqlDao<T> queueSqlDao) {
        if (candidates.isEmpty()) {
            return ImmutableList.of();
        }

        final Date now = utcNow.toDate();
        final Date nextAvailable = utcNow.plus(config.getClaimedTime().getMillis()).toDate();
        final String owner = CreatorName.get();
        final Collection<Long> recordIds = Collections2.transform(candidates, new Function<T, Long>() {
            @Override
            public Long apply(final T input) {
                return input == null ? Long.valueOf(-1) : input.getRecordId();
            }
        });

        final long ini = System.nanoTime();
        final int resultCount = queueSqlDao.claimEntries(recordIds, owner, nextAvailable, config.getTableName());
        rawClaimEntriesTime.update(System.nanoTime() - ini, TimeUnit.NANOSECONDS);

        // We should ALWAYS see the same number since we are in STICKY_POLLING mode and there is only one thread claiming entries.
        // We keep the 2 cases below for safety (code was written when this was MT-threaded), and we log with warn (will eventually remove it in the future)
        if (resultCount == candidates.size()) {
            log.debug("{} batchClaimEntries claimed (recordIds={}, now={}, nextAvailable={}, owner={}): {}",
                      DB_QUEUE_LOG_ID, recordIds, now, nextAvailable, owner, candidates);
            return candidates;
        } else {
            final List<T> maybeClaimedEntries = queueSqlDao.getEntriesFromIds(ImmutableList.copyOf(recordIds), config.getTableName());
            final StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < maybeClaimedEntries.size(); i++) {
                final T eventEntryModelDao = maybeClaimedEntries.get(i);
                if (i > 0) {
                    stringBuilder.append(",");
                }
                stringBuilder.append("[recordId=").append(eventEntryModelDao.getRecordId())
                             .append(",processingState=").append(eventEntryModelDao.getProcessingState())
                             .append(",processingOwner=").append(eventEntryModelDao.getProcessingOwner())
                             .append(",processingAvailableDate=").append(eventEntryModelDao.getNextAvailableDate())
                             .append("]");
            }
            log.warn("{} batchClaimEntries only claimed partial entries {}/{} (now={}, nextAvailable={}, owner={}): {}",
                     DB_QUEUE_LOG_ID, resultCount, candidates.size(), now, nextAvailable, owner, stringBuilder.toString());

            final Iterable<T> claimed = Iterables.<T>filter(maybeClaimedEntries, new Predicate<T>() {
                @Override
                public boolean apply(final T input) {
                    return input != null && input.getProcessingState() == PersistentQueueEntryLifecycleState.IN_PROCESSING && owner.equals(input.getProcessingOwner());
                }
            });
            return ImmutableList.<T>copyOf(claimed);
        }
    }

    //
    // In non sticky mode, we don't optimize claim update because we can't synchronize easily -- we could rely on global lock,
    // but we are looking for performance and that does not the right choice.
    //
    private List<T> sequentialClaimEntries(final DateTime now, final List<T> candidates, final QueueSqlDao<T> queueSqlDao) {
        return ImmutableList.<T>copyOf(Collections2.filter(candidates, new Predicate<T>() {
            @Override
            public boolean apply(final T input) {
                return claimEntry(now, input, queueSqlDao);
            }
        }));
    }

    private boolean claimEntry(final DateTime now, final T entry, final QueueSqlDao<T> queueSqlDao) {
        final Date nextAvailable = now.plus(config.getClaimedTime().getMillis()).toDate();

        final long ini = System.nanoTime();
        final int claimEntry = queueSqlDao.claimEntry(entry.getRecordId(), CreatorName.get(), nextAvailable, config.getTableName());
        rawClaimEntryTime.update(System.nanoTime() - ini, TimeUnit.NANOSECONDS);

        final boolean claimed = (claimEntry == 1);
        if (claimed) {
            log.debug("{} Claimed entry {}", DB_QUEUE_LOG_ID, entry);
        }
        return claimed;
    }
}
