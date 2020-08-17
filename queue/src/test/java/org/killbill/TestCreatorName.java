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

package org.killbill;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestCreatorName {

    @Test(groups = "fast")
    public void testNameTooLong() {
        final String oldProperty = System.getProperty(CreatorName.QUEUE_CREATOR_NAME);
        CreatorName.reset();

        try {
            System.setProperty(CreatorName.QUEUE_CREATOR_NAME, "testing-worker-linux-08b40318-1-526-linux-12-55205367");
            Assert.assertEquals(CreatorName.get(), "testing-worker-linux-08b40318-1-526-linux-12-");
        } finally {
            if (oldProperty != null) {
                System.setProperty(CreatorName.QUEUE_CREATOR_NAME, oldProperty);
            }
            CreatorName.reset();
        }
    }
}
