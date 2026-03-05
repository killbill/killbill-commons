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

import java.io.IOException;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.jooby.MediaType;
import org.jooby.Sse;
import org.jooby.servlet.ServletServletRequest;
import org.jooby.servlet.ServletUpgrade;
import org.jooby.spi.HttpHandler;
import org.jooby.spi.NativePushPromise;
import org.jooby.spi.NativeWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JettyHandler extends AbstractHandler {

  private static final String MULTIPART_CONFIG_ELEMENT = "org.eclipse.jetty.multipartConfig";

  /** The logging system. */
  private final Logger log = LoggerFactory.getLogger(getClass());

  private HttpHandler dispatcher;

  private String tmpdir;

  private MultipartConfigElement multiPartConfig;

  public JettyHandler(final HttpHandler dispatcher,
      final String tmpdir, final int fileSizeThreshold) {
    this.dispatcher = dispatcher;
    this.tmpdir = tmpdir;
    this.multiPartConfig = new MultipartConfigElement(tmpdir, -1L, -1L, fileSizeThreshold);
  }

  @Override
  public void handle(final String target, final Request baseRequest,
      final HttpServletRequest request, final HttpServletResponse response) throws IOException,
      ServletException {
    try {

      baseRequest.setHandled(true);

      String type = baseRequest.getContentType();
      boolean multipart = false;
      if (type != null && type.toLowerCase().startsWith(MediaType.multipart.name())) {
        baseRequest.setAttribute(MULTIPART_CONFIG_ELEMENT, multiPartConfig);
        multipart = true;
      }

      ServletServletRequest nreq = new ServletServletRequest(request, tmpdir, multipart)
          .with(upgrade(baseRequest, response));
      dispatcher.handle(nreq, new JettyResponse(nreq, response));
    } catch (IOException | ServletException | RuntimeException ex) {
      baseRequest.setHandled(false);
      log.error("execution of: " + target + " resulted in error", ex);
      throw ex;
    } catch (Throwable ex) {
      baseRequest.setHandled(false);
      log.error("execution of: " + target + " resulted in error", ex);
      throw new IllegalStateException(ex);
    }
  }

  private static ServletUpgrade upgrade(final Request baseRequest,
      final HttpServletResponse response) {
    return new ServletUpgrade() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> T upgrade(final Class<T> type) throws Exception {
        if (type == Sse.class) {
          return (T) new JettySse(baseRequest, (Response) response);
        } else if (type == NativePushPromise.class) {
          return (T) new JettyPush(baseRequest);
        }
        throw new UnsupportedOperationException("Not Supported: " + type);
      }
    };
  }

}
