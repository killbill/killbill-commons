/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.commons.jdbi.log;

import org.skife.jdbi.v2.logging.FormattedLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jLogging extends FormattedLog {

    private final Logger logger;
    private final LogLevel logLevel;

    public Slf4jLogging() {
        this(LoggerFactory.getLogger(Slf4jLogging.class));
    }

    public Slf4jLogging(final Logger logger) {
        this(logger, LogLevel.DEBUG);
    }

    public Slf4jLogging(final Logger logger, final LogLevel logLevel) {
        this.logger = logger;
        this.logLevel = logLevel;
    }

    /**
     * Used to ask implementations if logging is enabled.
     *
     * @return true if statement logging is enabled
     */
    @Override
    protected boolean isEnabled() {
        if (logLevel == LogLevel.DEBUG) {
            return logger.isDebugEnabled();
        } else if (logLevel == LogLevel.TRACE) {
            return logger.isTraceEnabled();
        } else if (logLevel == LogLevel.INFO) {
            return logger.isInfoEnabled();
        } else if (logLevel == LogLevel.WARN) {
            return logger.isWarnEnabled();
        } else if (logLevel == LogLevel.ERROR) {
            return logger.isErrorEnabled();
        } else {
            return false;
        }
    }

    /**
     * Log the statement passed in
     *
     * @param msg the message to log
     */
    @Override
    protected void log(final String msg) {
        if (logLevel.equals(LogLevel.DEBUG)) {
            logger.debug(msg);
        } else if (logLevel.equals(LogLevel.TRACE)) {
            logger.trace(msg);
        } else if (logLevel.equals(LogLevel.INFO)) {
            logger.info(msg);
        } else if (logLevel.equals(LogLevel.WARN)) {
            logger.warn(msg);
        } else if (logLevel.equals(LogLevel.ERROR)) {
            logger.error(msg);
        }
    }
}
