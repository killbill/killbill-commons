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
package org.jooby;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

import org.jooby.test.MockUnit;
import org.jooby.test.MockUnit.Block;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({RequestLogger.class, System.class })
public class RequestLoggerTest {

  @BeforeClass
  public static void before() {
    Locale.setDefault(Locale.US);
  }

  private Block capture = unit -> {
    Response rsp = unit.get(Response.class);
    rsp.complete(unit.capture(Route.Complete.class));
  };

  private Block onComplete = unit -> {
    unit.captured(Route.Complete.class).iterator().next()
        .handle(unit.get(Request.class), unit.get(Response.class), Optional.empty());
  };

  @Test
  public void basicUsage() throws Exception {
    new MockUnit(Request.class, Response.class)
        .expect(capture)
        .expect(timestamp(1L))
        .expect(ip("127.0.0.1"))
        .expect(method("GET"))
        .expect(path("/"))
        .expect(protocol("HTTP/1.1"))
        .expect(status(Status.OK))
        .expect(len(345L))
        .run(unit -> {
          new RequestLogger()
              .dateFormatter(ZoneId.of("UTC"))
              .handle(unit.get(Request.class), unit.get(Response.class));
        }, onComplete);
  }

  @Test
  public void latency() throws Exception {
    new MockUnit(Request.class, Response.class)
        .expect(capture)
        .expect(timestamp(7L))
        .expect(ip("127.0.0.1"))
        .expect(method("GET"))
        .expect(path("/"))
        .expect(protocol("HTTP/1.1"))
        .expect(status(Status.OK))
        .expect(len(345L))
        .expect(unit -> {
          unit.mockStatic(System.class);
          expect(System.currentTimeMillis()).andReturn(10L);
        })
        .run(unit -> {
          new RequestLogger()
              .dateFormatter(ZoneId.of("UTC"))
              .latency()
              .log(line -> assertEquals(
                  "127.0.0.1 - - [01/Jan/1970:00:00:00 +0000] \"GET / HTTP/1.1\" 200 345 3", line))
              .handle(unit.get(Request.class), unit.get(Response.class));
        }, onComplete);
  }

  @Test
  public void queryString() throws Exception {
    new MockUnit(Request.class, Response.class)
        .expect(capture)
        .expect(timestamp(7L))
        .expect(ip("127.0.0.1"))
        .expect(method("GET"))
        .expect(path("/path"))
        .expect(query("query=true"))
        .expect(protocol("HTTP/1.1"))
        .expect(status(Status.OK))
        .expect(len(345L))
        .run(unit -> {
          new RequestLogger()
              .dateFormatter(ZoneId.of("UTC"))
              .queryString()
              .log(line -> assertEquals(
                  "127.0.0.1 - - [01/Jan/1970:00:00:00 +0000] \"GET /path?query=true HTTP/1.1\" 200 345", line))
              .handle(unit.get(Request.class), unit.get(Response.class));
        }, onComplete);
  }

  @Test
  public void extended() throws Exception {
    new MockUnit(Request.class, Response.class)
        .expect(capture)
        .expect(timestamp(7L))
        .expect(ip("127.0.0.1"))
        .expect(method("GET"))
        .expect(path("/"))
        .expect(protocol("HTTP/1.1"))
        .expect(status(Status.OK))
        .expect(len(345L))
        .expect(referer("/referer"))
        .expect(userAgent("ugent"))
        .run(unit -> {
          new RequestLogger()
              .dateFormatter(ZoneId.of("UTC"))
              .extended()
              .log(line -> assertEquals(
                  "127.0.0.1 - - [01/Jan/1970:00:00:00 +0000] \"GET / HTTP/1.1\" 200 345 \"/referer\" \"ugent\"", line))
              .handle(unit.get(Request.class), unit.get(Response.class));
        }, onComplete);
  }

  private Block referer(final String referer) {
    return unit -> {
      Mutant mutant = unit.mock(Mutant.class);
      expect(mutant.value("-")).andReturn(referer);

      Request req = unit.get(Request.class);
      expect(req.header("Referer")).andReturn(mutant);
    };
  }

  private Block userAgent(final String userAgent) {
    return unit -> {
      Mutant mutant = unit.mock(Mutant.class);
      expect(mutant.value("-")).andReturn(userAgent);

      Request req = unit.get(Request.class);
      expect(req.header("User-Agent")).andReturn(mutant);
    };
  }

  @Test
  public void customLog() throws Exception {
    new MockUnit(Request.class, Response.class)
        .expect(capture)
        .expect(timestamp(1L))
        .expect(ip("127.0.0.1"))
        .expect(method("GET"))
        .expect(path("/"))
        .expect(protocol("HTTP/1.1"))
        .expect(status(Status.OK))
        .expect(len(345L))
        .run(unit -> {
          new RequestLogger()
              .dateFormatter(ZoneId.of("UTC"))
              .log(line -> assertEquals(
                  "127.0.0.1 - - [01/Jan/1970:00:00:00 +0000] \"GET / HTTP/1.1\" 200 345", line))
              .handle(unit.get(Request.class), unit.get(Response.class));
        }, onComplete);
  }

  private Block method(final String method) {
    return unit -> {
      Request req = unit.get(Request.class);
      expect(req.method()).andReturn(method);
    };
  }

  private Block path(final String path) {
    return unit -> {
      Request req = unit.get(Request.class);
      expect(req.path()).andReturn(path);
    };
  }

  private Block query(final String query) {
    return unit -> {
      Request req = unit.get(Request.class);
      expect(req.queryString()).andReturn(Optional.of(query));
    };
  }

  private Block status(final Status status) {
    return unit -> {
      Response rsp = unit.get(Response.class);
      expect(rsp.status()).andReturn(Optional.ofNullable(status));
    };
  }

  private Block len(final Long len) {
    return unit -> {
      Mutant mutant = unit.mock(Mutant.class);
      expect(mutant.value("-")).andReturn(len.toString());

      Response rsp = unit.get(Response.class);
      expect(rsp.header("Content-Length")).andReturn(mutant);
    };
  }

  private Block protocol(final String protocol) {
    return unit -> {
      Request req = unit.get(Request.class);
      expect(req.protocol()).andReturn(protocol);
    };
  }

  private Block timestamp(final long ts) {
    return unit -> {
      Request req = unit.get(Request.class);
      expect(req.timestamp()).andReturn(ts);
    };
  }

  private Block ip(final String ip) {
    return unit -> {
      Request req = unit.get(Request.class);
      expect(req.ip()).andReturn(ip);
    };
  }

}
