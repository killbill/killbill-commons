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
package org.jooby.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.jooby.Err;
import org.jooby.Mutant;
import org.jooby.Request;
import org.jooby.Response;
import org.jooby.Route;
import org.jooby.Session;
import org.junit.Test;

public class CsrfHandlerTest {

  @Test
  public void invalidTokenDoesNotEchoCandidate() throws Throwable {
    Request req = mock(Request.class);
    Response rsp = mock(Response.class);
    Route.Chain chain = mock(Route.Chain.class);
    Session session = mock(Session.class);
    Mutant tokenMutant = mock(Mutant.class);
    Mutant headerMutant = mock(Mutant.class);
    Mutant paramMutant = mock(Mutant.class);

    when(req.session()).thenReturn(session);
    when(req.method()).thenReturn("POST");
    when(session.get("csrf")).thenReturn(tokenMutant);
    when(tokenMutant.toOptional()).thenReturn(Optional.of("real-token"));
    when(req.header("csrf")).thenReturn(headerMutant);
    when(headerMutant.toOptional()).thenReturn(Optional.empty());
    when(req.param("csrf")).thenReturn(paramMutant);
    when(paramMutant.toOptional()).thenReturn(Optional.of("attacker-token"));
    when(req.set(anyString(), any())).thenReturn(req);

    CsrfHandler handler = new CsrfHandler();
    try {
      handler.handle(req, rsp, chain);
      fail("Expected Err to be thrown");
    } catch (Err err) {
      assertEquals(403, err.statusCode());
      assertTrue("Error message should contain 'Invalid CSRF token'",
          err.getMessage().contains("Invalid CSRF token"));
      assertTrue("Error message must not contain the candidate token",
          !err.getMessage().contains("attacker-token"));
    }
  }

  @Test
  public void validTokenPassesThrough() throws Throwable {
    Request req = mock(Request.class);
    Response rsp = mock(Response.class);
    Route.Chain chain = mock(Route.Chain.class);
    Session session = mock(Session.class);
    Mutant tokenMutant = mock(Mutant.class);
    Mutant headerMutant = mock(Mutant.class);

    String token = "valid-token";

    when(req.session()).thenReturn(session);
    when(req.method()).thenReturn("POST");
    when(session.get("csrf")).thenReturn(tokenMutant);
    when(tokenMutant.toOptional()).thenReturn(Optional.of(token));
    when(req.header("csrf")).thenReturn(headerMutant);
    when(headerMutant.toOptional()).thenReturn(Optional.of(token));
    when(req.set(anyString(), any())).thenReturn(req);

    CsrfHandler handler = new CsrfHandler();
    handler.handle(req, rsp, chain);

    verify(chain).next(req, rsp);
  }

  @Test
  public void getRequestSkipsTokenVerification() throws Throwable {
    Request req = mock(Request.class);
    Response rsp = mock(Response.class);
    Route.Chain chain = mock(Route.Chain.class);
    Session session = mock(Session.class);
    Mutant tokenMutant = mock(Mutant.class);

    when(req.session()).thenReturn(session);
    when(req.method()).thenReturn("GET");
    when(session.get("csrf")).thenReturn(tokenMutant);
    when(tokenMutant.toOptional()).thenReturn(Optional.of("some-token"));
    when(req.set(anyString(), any())).thenReturn(req);

    CsrfHandler handler = new CsrfHandler();
    handler.handle(req, rsp, chain);

    verify(chain).next(req, rsp);
  }

  @Test
  public void missingTokenThrowsForbidden() throws Throwable {
    Request req = mock(Request.class);
    Response rsp = mock(Response.class);
    Route.Chain chain = mock(Route.Chain.class);
    Session session = mock(Session.class);
    Mutant tokenMutant = mock(Mutant.class);
    Mutant headerMutant = mock(Mutant.class);
    Mutant paramMutant = mock(Mutant.class);

    when(req.session()).thenReturn(session);
    when(req.method()).thenReturn("POST");
    when(session.get("csrf")).thenReturn(tokenMutant);
    when(tokenMutant.toOptional()).thenReturn(Optional.of("real-token"));
    when(req.header("csrf")).thenReturn(headerMutant);
    when(headerMutant.toOptional()).thenReturn(Optional.empty());
    when(req.param("csrf")).thenReturn(paramMutant);
    when(paramMutant.toOptional()).thenReturn(Optional.empty());
    when(req.set(anyString(), any())).thenReturn(req);

    CsrfHandler handler = new CsrfHandler();
    try {
      handler.handle(req, rsp, chain);
      fail("Expected Err to be thrown");
    } catch (Err err) {
      assertEquals(403, err.statusCode());
      assertTrue("Error message should contain 'Invalid CSRF token'",
          err.getMessage().contains("Invalid CSRF token"));
    }
  }
}
