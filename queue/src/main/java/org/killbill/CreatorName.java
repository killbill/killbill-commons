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


import java.net.InetAddress;
import java.net.UnknownHostException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

public class CreatorName {

    private static final Object lock = new Object();

    // Allow to override the default naming based on Hostname
    static final String QUEUE_CREATOR_NAME = "org.killbill.queue.creator.name";

    private static String creatorName;

    public static String get() {
        if (creatorName == null) {
            synchronized (lock) {
                String tmpCreatorName = System.getProperty(QUEUE_CREATOR_NAME);
                if (Strings.emptyToNull(tmpCreatorName) == null) {
                    try {
                        final InetAddress addr = InetAddress.getLocalHost();
                        tmpCreatorName = addr.getHostName();
                    } catch (final UnknownHostException e) {
                        tmpCreatorName = "creatorName-unknown";
                    }
                }
                creatorName = tmpCreatorName.length() > 45 ? tmpCreatorName.substring(0, 45) : tmpCreatorName;
            }
        }
        return creatorName;
    }

    @VisibleForTesting
    static synchronized void reset() {
        creatorName = null;
    }
}
