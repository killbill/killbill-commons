/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Hostname {

    private static final Logger logger = LoggerFactory.getLogger(Hostname.class);

    private static String hostname;

    // Can be used for testing with multiple instances on the same machine (with same  InetAddress)
    private static final String POSTFIX_HOSTNAME = "org.killbill.hostname.postfix";

    public static String get() {
        if (hostname == null) {
            synchronized (Hostname.class) {
                if (hostname == null) {
                    try {
                        final InetAddress addr = InetAddress.getLocalHost();
                        hostname = addr.getHostName();
                    } catch (UnknownHostException e) {
                        hostname = "hostname-unknown";
                    }
                    if (System.getProperty(POSTFIX_HOSTNAME) != null) {
                        hostname = hostname + System.getProperty(POSTFIX_HOSTNAME);
                        logger.warn("Found system property " + POSTFIX_HOSTNAME + ": hostname for queue = " + hostname);
                    }
                }
            }
        }
        return hostname;
    }
}
