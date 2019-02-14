/*
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
                 config.getPersistentQueueMode());
    }

    @Override
    public void insertEntryFromTransaction(final QueueSqlDao<T> transactional, final T entry) {
        safeInsertEntry(transactional, entry);
    }

    @Override
    public List<T> getReadyEntries() {
        final List<T> entriesToClaim = fetchReadyEntries(config.getMaxEntriesClaimed());
        if (!entriesToClaim.isEmpty()) {
            log.debug("{} Entries to claim: {}", DB_QUEUE_LOG_ID, entriesToClaim);
            return claimEntries(entriesToClaim);
        }
        return ImmutableList.<T>of();
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
        if (config.getPersistentQueueMode() == PersistentQueueMode.STICKY_POLLING) {
            for (final T entry : entriesLeftBehind) {
                entry.setCreatedDate(now);
                entry.setProcessingState(PersistentQueueEntryLifecycleState.AVAILABLE);
                entry.setCreatingOwner(CreatorName.get());
            }
            transactional.insertEntries(entriesLeftBehind, config.getTableName());
        }
    }

    private List<T> claimEntries(final List<T> candidates) {
        switch (config.getPersistentQueueMode()) {
            case POLLING:
                return sequentialClaimEntries(candidates);

            case STICKY_POLLING:
                return batchClaimEntries(candidates);

            default:
                throw new IllegalStateException("Unsupported PersistentQueueMode " + config.getPersistentQueueMode());
        }
    }

    private List<T> batchClaimEntries(final List<T> candidates) {
        if (candidates.isEmpty()) {
            return ImmutableList.of();
        }
        final Date nextAvailable = clock.getUTCNow().plus(config.getClaimedTime().getMillis()).toDate();
        final Collection<Long> recordIds = Collections2.transform(candidates, new Function<T, Long>() {
            @Override
            public Long apply(final T input) {
                return input.getRecordId();
            }
        });
        final int resultCount = executeQuery(new Query<Integer, QueueSqlDao<T>>() {
            @Override
            public Integer execute(final QueueSqlDao<T> queueSqlDao) {
                long ini = System.nanoTime();
                final Integer result = queueSqlDao.claimEntries(recordIds, clock.getUTCNow().toDate(), CreatorName.get(), nextAvailable, config.getTableName());
                claimTime.update(System.nanoTime() - ini, TimeUnit.NANOSECONDS);
                return result;
            }
        });
        // We should ALWAYS see the same number since we are in STICKY_POLLING mode and there is only one thread claiming entries.
        // We keep the 2 cases below for safety (code was written when this was MT-threaded), and we log with warn (will eventually remove it in the future)
        if (resultCount == candidates.size()) {
            log.debug("{} batchClaimEntries claimed: {}", DB_QUEUE_LOG_ID, candidates);
            return candidates;
            // Nothing... the synchronized block let go another concurrent thread
        } else if (resultCount == 0) {
            log.warn("{} batchClaimEntries see 0 entries", DB_QUEUE_LOG_ID);
            return ImmutableList.of();
        } else {
            final List<T> maybeClaimedEntries = executeQuery(new Query<List<T>, QueueSqlDao<T>>() {
                @Override
                public List<T> execute(final QueueSqlDao<T> queueSqlDao) {
                    return queueSqlDao.getEntriesFromIds(ImmutableList.copyOf(recordIds), config.getTableName());
                }
            });
            final Iterable<T> claimed = Iterables.<T>filter(maybeClaimedEntries, new Predicate<T>() {
                @Override
                public boolean apply(final T input) {
                    return input.getProcessingState() == PersistentQueueEntryLifecycleState.IN_PROCESSING && input.getProcessingOwner().equals(CreatorName.get());
                }
            });

            final List<T> result = ImmutableList.<T>copyOf(claimed);

            log.warn("{} batchClaimEntries only claimed partial entries {}/{}", DB_QUEUE_LOG_ID, result.size(), candidates.size());
            return result;
        }
    }

    //
    // In non sticky mode, we don't optimize claim update because we can't synchronize easily -- we could rely on global lock,
    // but we are looking for performance and that does not the right choice.
    //
    private List<T> sequentialClaimEntries(final List<T> candidates) {
        return ImmutableList.<T>copyOf(Collections2.filter(candidates, new Predicate<T>() {
            @Override
            public boolean apply(final T input) {
                return claimEntry(input);
            }
        }));
    }

    private boolean claimEntry(final T entry) {
        final Date nextAvailable = clock.getUTCNow().plus(config.getClaimedTime().getMillis()).toDate();
        final int claimEntry = executeQuery(new Query<Integer, QueueSqlDao<T>>() {
            @Override
            public Integer execute(final QueueSqlDao<T> queueSqlDao) {
                long ini = System.nanoTime();
                final Integer result = queueSqlDao.claimEntry(entry.getRecordId(), clock.getUTCNow().toDate(), CreatorName.get(), nextAvailable, config.getTableName());
                claimTime.update(System.nanoTime() - ini, TimeUnit.NANOSECONDS);
                return result;
            }
        });
        final boolean claimed = (claimEntry == 1);

        if (claimed) {
            log.debug("{} Claimed entry {}", DB_QUEUE_LOG_ID, entry);
        }
        return claimed;
    }

}
