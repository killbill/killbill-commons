/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.killbill.commons.utils;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Verbatim copy of guava's com.google.common.base.Throwables (v.31.0.1).
 * Only includes the methods used within killbill.
 *
 * @see <a href="https://github.com/killbill/killbill/issues/1615">Guava removal tracking</a>
 */
public final class Throwables {

    private Throwables() {}

    /**
     * Returns a string containing the result of {@link Throwable#toString() toString()}, followed by
     * the full, recursive stack trace of {@code throwable}. Note that you probably should not be
     * parsing the resulting string; if you need programmatic access to the stack frames, you can call
     * {@link Throwable#getStackTrace()}.
     */
    public static String getStackTraceAsString(final Throwable throwable) {
        final StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }
}
