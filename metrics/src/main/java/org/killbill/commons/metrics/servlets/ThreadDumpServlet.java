/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.commons.metrics.servlets;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.Thread.State;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ThreadDumpServlet extends HttpServlet {

    private static final long serialVersionUID = -5131980901584483867L;
    private static final String CONTENT_TYPE = "text/plain";

    private transient ThreadDump threadDump;

    private static Boolean getParam(final String initParam, final boolean defaultValue) {
        return initParam == null ? defaultValue : Boolean.parseBoolean(initParam);
    }

    @Override
    public void init() {
        try {
            // Some PaaS like Google App Engine blacklist java.lang.managament
            this.threadDump = new ThreadDump(ManagementFactory.getThreadMXBean());
        } catch (final NoClassDefFoundError ncdfe) {
            this.threadDump = null; // we won't be able to provide thread dump
        }
    }

    @Override
    protected void doGet(final HttpServletRequest req,
                         final HttpServletResponse resp) throws IOException {
        final boolean includeMonitors = getParam(req.getParameter("monitors"), true);
        final boolean includeSynchronizers = getParam(req.getParameter("synchronizers"), true);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType(CONTENT_TYPE);
        resp.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");
        if (threadDump == null) {
            resp.getWriter().println("Sorry your runtime environment does not allow to dump threads.");
            return;
        }
        try (final OutputStream output = resp.getOutputStream()) {
            threadDump.dump(includeMonitors, includeSynchronizers, output);
        }
    }

    public static class ThreadDump {

        private final ThreadMXBean threadMXBean;

        public ThreadDump(final ThreadMXBean threadMXBean) {
            this.threadMXBean = threadMXBean;
        }

        public void dump(final OutputStream out) {
            this.dump(true, true, out);
        }

        public void dump(final boolean lockedMonitors, final boolean lockedSynchronizers, final OutputStream out) {
            final ThreadInfo[] threads = this.threadMXBean.dumpAllThreads(lockedMonitors, lockedSynchronizers);
            final PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

            for (int ti = threads.length - 1; ti >= 0; --ti) {
                final ThreadInfo t = threads[ti];
                writer.printf("\"%s\" id=%d state=%s", t.getThreadName(), t.getThreadId(), t.getThreadState());
                final LockInfo lock = t.getLockInfo();
                if (lock != null && t.getThreadState() != State.BLOCKED) {
                    writer.printf("%n    - waiting on <0x%08x> (a %s)", lock.getIdentityHashCode(), lock.getClassName());
                    writer.printf("%n    - locked <0x%08x> (a %s)", lock.getIdentityHashCode(), lock.getClassName());
                } else if (lock != null && t.getThreadState() == State.BLOCKED) {
                    writer.printf("%n    - waiting to lock <0x%08x> (a %s)", lock.getIdentityHashCode(), lock.getClassName());
                }

                if (t.isSuspended()) {
                    writer.print(" (suspended)");
                }

                if (t.isInNative()) {
                    writer.print(" (running in native)");
                }

                writer.println();
                if (t.getLockOwnerName() != null) {
                    writer.printf("     owned by %s id=%d%n", t.getLockOwnerName(), t.getLockOwnerId());
                }

                final StackTraceElement[] elements = t.getStackTrace();
                final MonitorInfo[] monitors = t.getLockedMonitors();

                int j;
                for (int i = 0; i < elements.length; ++i) {
                    final StackTraceElement element = elements[i];
                    writer.printf("    at %s%n", element);

                    for (j = 1; j < monitors.length; ++j) {
                        final MonitorInfo monitor = monitors[j];
                        if (monitor.getLockedStackDepth() == i) {
                            writer.printf("      - locked %s%n", monitor);
                        }
                    }
                }

                writer.println();
                final LockInfo[] locks = t.getLockedSynchronizers();
                if (locks.length > 0) {
                    writer.printf("    Locked synchronizers: count = %d%n", locks.length);
                    final LockInfo[] var17 = locks;
                    j = locks.length;

                    for (int var18 = 0; var18 < j; ++var18) {
                        final LockInfo l = var17[var18];
                        writer.printf("      - %s%n", l);
                    }

                    writer.println();
                }
            }

            writer.println();
            writer.flush();
        }
    }
}
