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
package org.jooby.internal.jetty;

import java.util.Map;

import org.eclipse.jetty.server.Request;
import org.jooby.spi.NativePushPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JettyPush implements NativePushPromise {

  private static final Logger log = LoggerFactory.getLogger(JettyPush.class);

  private Request req;

  public JettyPush(final Request req) {
    this.req = req;
  }

  @Override
  public void push(final String method, final String path, final Map<String, Object> headers) {
    // HTTP/2 Server Push (PushBuilder) was removed in Jetty 10 / Servlet 5.0.
    // It is deprecated in the HTTP/2 spec (RFC 9113) and unsupported by most browsers.
    log.debug("HTTP/2 push ignored (not supported in Jetty 10+): {} {}", method, path);
  }

}
