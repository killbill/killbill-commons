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

package org.killbill.bus.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.killbill.TestSetup;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestBusSqlDao extends TestSetup {

    private static final String hostname = "Hip";

    private static final long SEARCH_KEY_2 = 39;

    private PersistentBusSqlDao dao;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        super.beforeClass();
        dao = getDBI().onDemand(PersistentBusSqlDao.class);
    }

    // Verify we retrieve all existing ready recordIds -- with no dups -- using a batch approach.
    @Test(groups = "slow")
    public void testGetReadyEntryIds() {
        final long searchKey1 = 1242L;

        final int TOTAL_ENTRIES = 1543;
        final ArrayList<BusEventModelDao> entries = new ArrayList<>(TOTAL_ENTRIES);
        for (int i = 0; i < TOTAL_ENTRIES; i++) {
            final String eventJson = String.valueOf(i);
            final BusEventModelDao e = new BusEventModelDao(hostname, clock.getUTCNow(), eventJson.getClass().toString(), eventJson, UUID.randomUUID(), searchKey1, SEARCH_KEY_2);
            entries.add(e);
        }
        dao.insertEntries(entries, persistentBusConfig.getTableName());

        int remaining = TOTAL_ENTRIES;
        final int BATCH_SIZE = 100;

        int totalEntries = 0;
        long fromRecordId = -1;
        while (remaining > 0) {
            //System.err.print(String.format("from = %d\n", fromRecordId));
            final List<Long> curBatch = dao.getReadyEntryIds(clock.getUTCNow().toDate(), fromRecordId, BATCH_SIZE, hostname, persistentBusConfig.getTableName());
            if (remaining / BATCH_SIZE > 0) {
                assertEquals(curBatch.size(), BATCH_SIZE);
            } else {
                assertEquals(curBatch.size(), remaining);
            }
            for (Long e : curBatch) {
                if (fromRecordId == -1) {
                    fromRecordId = e;
                } else {
                    assertEquals(e.longValue(), fromRecordId);
                }
                fromRecordId++;
            }
            remaining -= curBatch.size();
            totalEntries += curBatch.size();
        }
        assertEquals(totalEntries, TOTAL_ENTRIES);
    }

}
