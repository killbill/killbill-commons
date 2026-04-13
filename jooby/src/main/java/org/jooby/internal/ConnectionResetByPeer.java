/*
 * Copyright 2026 The Billing Project, LLC
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
package org.jooby.internal;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class ConnectionResetByPeer {

  public static boolean test(final Throwable cause) {
    return Optional.ofNullable(cause)
        .filter(IOException.class::isInstance)
        .map(x -> x.getMessage())
        .filter(Objects::nonNull)
        .map(message -> message.toLowerCase().contains("connection reset by peer"))
        .orElse(false);
  }
}
