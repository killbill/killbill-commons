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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.jooby.Sse;
import org.jooby.servlet.ServletServletRequest;
import org.jooby.spi.HttpHandler;
import org.jooby.spi.NativePushPromise;
import org.jooby.spi.NativeRequest;
import org.jooby.spi.NativeResponse;
import org.junit.Test;
import org.mockito.Mockito;

public class JettyHandlerTest {

  @Test
  public void handleShouldSetMultipartConfig() throws Exception {
    Request baseRequest = Mockito.mock(Request.class);
    HttpServletRequest request = newRequest();
    Response response = Mockito.mock(Response.class);
    AtomicReference<NativeRequest> capturedRequest = new AtomicReference<>();
    AtomicReference<NativeResponse> capturedResponse = new AtomicReference<>();

    when(baseRequest.getContentType()).thenReturn("Multipart/Form-Data");

    HttpHandler dispatcher = (req, rsp) -> {
      capturedRequest.set(req);
      capturedResponse.set(rsp);
    };

    new JettyHandler(dispatcher, "target", -1).handle("/", baseRequest, request, response);

    verify(baseRequest).setHandled(true);
    verify(baseRequest).setAttribute(eq("org.eclipse.jetty.multipartConfig"),
        any(MultipartConfigElement.class));
    assertTrue(capturedRequest.get() instanceof ServletServletRequest);
    assertTrue(capturedResponse.get() instanceof JettyResponse);
  }

  @Test
  public void handleShouldIgnoreMultipartConfig() throws Exception {
    Request baseRequest = Mockito.mock(Request.class);
    HttpServletRequest request = newRequest();
    Response response = Mockito.mock(Response.class);

    when(baseRequest.getContentType()).thenReturn("application/json");

    HttpHandler dispatcher = (req, rsp) -> {
    };

    new JettyHandler(dispatcher, "target", -1).handle("/", baseRequest, request, response);

    verify(baseRequest).setHandled(true);
    verify(baseRequest, never()).setAttribute(eq("org.eclipse.jetty.multipartConfig"),
        any(MultipartConfigElement.class));
  }

  @Test
  public void handleShouldSupportSseUpgrade() throws Exception {
    Request baseRequest = Mockito.mock(Request.class);
    HttpServletRequest request = newRequest();
    Response response = Mockito.mock(Response.class);
    AtomicReference<ServletServletRequest> capturedRequest = new AtomicReference<>();

    when(baseRequest.getContentType()).thenReturn("application/json");

    HttpHandler dispatcher = (req, rsp) -> {
      capturedRequest.set((ServletServletRequest) req);
    };

    new JettyHandler(dispatcher, "target", -1).handle("/", baseRequest, request, response);

    assertTrue(capturedRequest.get().upgrade(Sse.class) instanceof JettySse);
  }

  @Test
  public void handleShouldSupportPushPromiseUpgrade() throws Exception {
    Request baseRequest = Mockito.mock(Request.class);
    HttpServletRequest request = newRequest();
    Response response = Mockito.mock(Response.class);
    AtomicReference<ServletServletRequest> capturedRequest = new AtomicReference<>();

    when(baseRequest.getContentType()).thenReturn("application/json");

    HttpHandler dispatcher = (req, rsp) -> {
      capturedRequest.set((ServletServletRequest) req);
    };

    new JettyHandler(dispatcher, "target", -1).handle("/", baseRequest, request, response);

    assertTrue(capturedRequest.get().upgrade(NativePushPromise.class) instanceof JettyPush);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void handleShouldRejectUnsupportedUpgradeType() throws Exception {
    Request baseRequest = Mockito.mock(Request.class);
    HttpServletRequest request = newRequest();
    Response response = Mockito.mock(Response.class);
    AtomicReference<ServletServletRequest> capturedRequest = new AtomicReference<>();

    when(baseRequest.getContentType()).thenReturn("application/json");

    HttpHandler dispatcher = (req, rsp) -> {
      capturedRequest.set((ServletServletRequest) req);
    };

    new JettyHandler(dispatcher, "target", -1).handle("/", baseRequest, request, response);

    capturedRequest.get().upgrade(JettyHandlerTest.class);
  }

  @Test(expected = ServletException.class)
  public void handleShouldReThrowServletException() throws Exception {
    shouldPropagate(new ServletException("intentional err"));
  }

  @Test(expected = IOException.class)
  public void handleShouldReThrowIOException() throws Exception {
    shouldPropagate(new IOException("intentional err"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void handleShouldReThrowRuntimeException() throws Exception {
    shouldPropagate(new IllegalArgumentException("intentional err"));
  }

  @Test
  public void handleShouldWrapCheckedThrowable() throws Exception {
    Request baseRequest = Mockito.mock(Request.class);
    HttpServletRequest request = newRequest();
    Response response = Mockito.mock(Response.class);

    when(baseRequest.getContentType()).thenReturn("application/json");

    HttpHandler dispatcher = (req, rsp) -> {
      throw new Exception("intentional err");
    };

    try {
      new JettyHandler(dispatcher, "target", -1).handle("/", baseRequest, request, response);
    } catch (IllegalStateException e) {
      assertEquals("intentional err", e.getCause().getMessage());
      verify(baseRequest).setHandled(false);
      return;
    }
    throw new AssertionError("Expected IllegalStateException");
  }

  private void shouldPropagate(final Exception cause) throws Exception {
    Request baseRequest = Mockito.mock(Request.class);
    HttpServletRequest request = newRequest();
    Response response = Mockito.mock(Response.class);

    when(baseRequest.getContentType()).thenReturn("application/json");

    HttpHandler dispatcher = (req, rsp) -> {
      throw cause;
    };

    try {
      new JettyHandler(dispatcher, "target", -1).handle("/", baseRequest, request, response);
    } finally {
      verify(baseRequest).setHandled(false);
    }
  }

  private HttpServletRequest newRequest() {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    when(request.getPathInfo()).thenReturn("/");
    when(request.getContextPath()).thenReturn("");
    return request;
  }
}
