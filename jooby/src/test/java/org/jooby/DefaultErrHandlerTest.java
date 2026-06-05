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

import org.killbill.commons.utils.html.HtmlEscapers;
import com.typesafe.config.Config;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import org.jooby.test.MockUnit;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultErrHandlerTest {

  private final AtomicReference<Result> capturedResult = new AtomicReference<>();

  @SuppressWarnings({"unchecked"})
  @Test
  public void handleNoErrMessage() throws Exception {
    Err ex = new Err(500);

    StringWriter writer = new StringWriter();
    ex.printStackTrace(new PrintWriter(writer));
    String[] stacktrace = writer.toString().replace("\r", "").split("\\n");

    new MockUnit(Request.class, Response.class, Route.class, Config.class, Env.class)
        .expect(handleErr(ex,true))
        .run(unit -> {

          Request req = unit.get(Request.class);
          Response rsp = unit.get(Response.class);

          new Err.DefHandler().handle(req, rsp, ex);
        }, unit -> {
          Result result = capturedResult.get();
          View view = (View) result.ifGet(List.of(MediaType.html)).get();
          assertEquals("err", view.name());
          checkErr(stacktrace, "Server Error(500)", (Map<String, Object>) view.model()
              .get("err"));

          Object hash = result.ifGet(MediaType.ALL).get();
          assertEquals(4, ((Map<String, Object>) hash).size());
        });
  }

  private MockUnit.Block handleErr(Throwable ex, boolean stacktrace) {
    return unit -> {
      Logger log = unit.mock(Logger.class);
      log.error("execution of: {}{} resulted in exception\nRoute:\n{}\n\nStacktrace:", "GET",
          "/path", "route", ex);

      unit.mockStatic(LoggerFactory.class).when(() -> LoggerFactory.getLogger(Err.class)).thenReturn(log);

      Route route = unit.get(Route.class);
      when(route.print(6)).thenReturn("route");

      Config conf = unit.get(Config.class);
      when(conf.getBoolean("err.stacktrace")).thenReturn(stacktrace);
      Env env = unit.get(Env.class);
      when(env.name()).thenReturn("dev");
      when(env.xss("html")).thenReturn(HtmlEscapers.htmlEscaper()::escape);

      Request req = unit.get(Request.class);

      when(req.require(Config.class)).thenReturn(conf);
      when(req.require(Env.class)).thenReturn(env);
      when(req.path()).thenReturn("/path");
      when(req.method()).thenReturn("GET");
      when(req.route()).thenReturn(route);

      Response rsp = unit.get(Response.class);

      doAnswer(inv -> { capturedResult.set(inv.getArgument(0)); return null; })
          .when(rsp).send(any(Result.class));
    };
  }

  @SuppressWarnings({"unchecked"})
  @Test
  public void handleWithErrMessage() throws Exception {
    Err ex = new Err(500, "Something something dark");

    StringWriter writer = new StringWriter();
    ex.printStackTrace(new PrintWriter(writer));
    String[] stacktrace = writer.toString().replace("\r", "").split("\\n");

    new MockUnit(Request.class, Response.class, Route.class, Env.class, Config.class)
        .expect(handleErr(ex, true))
        .run(unit -> {

              Request req = unit.get(Request.class);
              Response rsp = unit.get(Response.class);

              new Err.DefHandler().handle(req, rsp, ex);
            },
            unit -> {
              Result result = capturedResult.get();
              View view = (View) result.ifGet(List.of(MediaType.html)).get();
              assertEquals("err", view.name());
              checkErr(stacktrace, "Server Error(500): Something something dark",
                  (Map<String, Object>) view.model()
                      .get("err"));

              Object hash = result.ifGet(MediaType.ALL).get();
              assertEquals(4, ((Map<String, Object>) hash).size());
            });
  }

  @SuppressWarnings({"unchecked"})
  @Test
  public void handleWithHtmlErrMessage() throws Exception {
    Err ex = new Err(500, "Something something <em>dark</em>");

    StringWriter writer = new StringWriter();
    ex.printStackTrace(new PrintWriter(writer));
    String[] stacktrace = writer.toString().replace("\r", "").split("\\n");

    new MockUnit(Request.class, Response.class, Route.class, Env.class, Config.class)
            .expect(handleErr(ex, true))
            .run(unit -> {

                      Request req = unit.get(Request.class);
                      Response rsp = unit.get(Response.class);

                      new Err.DefHandler().handle(req, rsp, ex);
                    },
                    unit -> {
                      Result result = capturedResult.get();
                      View view = (View) result.ifGet(List.of(MediaType.html)).get();
                      assertEquals("err", view.name());
                      checkErr(stacktrace, "Server Error(500): Something something &lt;em&gt;dark&lt;/em&gt;",
                              (Map<String, Object>) view.model()
                                      .get("err"));

                      Object hash = result.ifGet(MediaType.ALL).get();
                      assertEquals(4, ((Map<String, Object>) hash).size());
                    });
  }

  private void checkErr(final String[] stacktrace, final String message,
      final Map<String, Object> err) {
    final Map<String, Object> copy = new LinkedHashMap<>(err);
    assertEquals(message, copy.remove("message"));
    assertEquals("Server Error", copy.remove("reason"));
    assertEquals(500, copy.remove("status"));
    assertArrayEquals(stacktrace, (String[]) copy.remove("stacktrace"));
    assertEquals(copy.toString(), 0, copy.size());
  }

}
