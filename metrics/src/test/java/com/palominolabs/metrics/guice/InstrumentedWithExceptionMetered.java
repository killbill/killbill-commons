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

package com.palominolabs.metrics.guice;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;

@SuppressWarnings("UnusedReturnValue")
class InstrumentedWithExceptionMetered {

    @ExceptionMetered(name = "exceptionCounter")
    String explodeWithPublicScope(final boolean explode) {
        if (explode) {
            throw new RuntimeException("Boom!");
        } else {
            return "calm";
        }
    }

    @ExceptionMetered
    String explodeForUnnamedMetric() {
        throw new RuntimeException("Boom!");
    }

    @ExceptionMetered(name = "n")
    String explodeForMetricWithName() {
        throw new RuntimeException("Boom!");
    }

    @ExceptionMetered(name = "absoluteName", absolute = true)
    String explodeForMetricWithAbsoluteName() {
        throw new RuntimeException("Boom!");
    }

    @ExceptionMetered
    String explodeWithDefaultScope() {
        throw new RuntimeException("Boom!");
    }

    @ExceptionMetered
    String explodeWithProtectedScope() {
        throw new RuntimeException("Boom!");
    }

    @ExceptionMetered(name = "failures", cause = MyException.class)
    void errorProneMethod(final RuntimeException e) {
        throw e;
    }

    @ExceptionMetered(name = "things",
            cause = ArrayIndexOutOfBoundsException.class)
    Object causeAnOutOfBoundsException() {
        @SuppressWarnings("MismatchedReadAndWriteOfArray") final Object[] arr = {};
        //noinspection ConstantConditions
        return arr[1];
    }

    @Timed
    @ExceptionMetered
    void timedAndException(final RuntimeException e) {
        if (e != null) {
            throw e;
        }
    }

    @Metered
    @ExceptionMetered
    void meteredAndException(final RuntimeException e) {
        if (e != null) {
            throw e;
        }
    }
}
