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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isA;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

import org.jooby.test.MockUnit;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class ResponseForwardingTest {

  @Test
  public void unwrap() throws Exception {
    new MockUnit(Response.class)
        .run(unit -> {
          Response rsp = unit.get(Response.class);

          assertEquals(rsp, Response.Forwarding.unwrap(new Response.Forwarding(rsp)));

          // 2 level
          assertEquals(rsp,
              Response.Forwarding.unwrap(new Response.Forwarding(new Response.Forwarding(rsp))));

          // 3 level
          assertEquals(rsp,
              Response.Forwarding.unwrap(new Response.Forwarding(new Response.Forwarding(
                  new Response.Forwarding(rsp)))));

        });
  }

  @Test
  public void type() throws Exception {
    new MockUnit(Response.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);

          when(rsp.type()).thenReturn(Optional.empty());

          when(rsp.type("json")).thenReturn(rsp);
          when(rsp.type(MediaType.js)).thenReturn(rsp);
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));

          assertEquals(Optional.empty(), rsp.type());
          assertEquals(rsp, rsp.type("json"));
          assertEquals(rsp, rsp.type(MediaType.js));
        });
  }

  @Test
  public void header() throws Exception {
    new MockUnit(Response.class, Mutant.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);
          when(rsp.header("h")).thenReturn(unit.get(Mutant.class));
        })
        .run(unit -> {
          assertEquals(unit.get(Mutant.class),
              new Response.Forwarding(unit.get(Response.class)).header("h"));
        });
  }

  @Test
  public void setheader() throws Exception {
    Date now = new Date();
    new MockUnit(Response.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);
          when(rsp.header("b", (byte) 1)).thenReturn(null);
          when(rsp.header("c", 'c')).thenReturn(null);
          when(rsp.header("s", "s")).thenReturn(null);
          when(rsp.header("d", now)).thenReturn(null);
          when(rsp.header("d", 3d)).thenReturn(null);
          when(rsp.header("f", 4f)).thenReturn(null);
          when(rsp.header("i", 8)).thenReturn(null);
          when(rsp.header("l", 9l)).thenReturn(null);
          when(rsp.header("s", (short) 2)).thenReturn(null);
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));
          assertEquals(rsp, rsp.header("b", (byte) 1));
          assertEquals(rsp, rsp.header("c", 'c'));
          assertEquals(rsp, rsp.header("s", "s"));
          assertEquals(rsp, rsp.header("d", now));
          assertEquals(rsp, rsp.header("d", 3d));
          assertEquals(rsp, rsp.header("f", 4f));
          assertEquals(rsp, rsp.header("i", 8));
          assertEquals(rsp, rsp.header("l", 9l));
          assertEquals(rsp, rsp.header("s", (short) 2));
        });
  }

  @Test
  public void cookie() throws Exception {
    new MockUnit(Response.class, Cookie.class, Cookie.Definition.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);
          when(rsp.cookie(unit.get(Cookie.class))).thenReturn(null);

          when(rsp.cookie(unit.get(Cookie.Definition.class))).thenReturn(null);

          when(rsp.cookie("name", "value")).thenReturn(null);
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));
          assertEquals(rsp, rsp.cookie(unit.get(Cookie.class)));
          assertEquals(rsp, rsp.cookie(unit.get(Cookie.Definition.class)));
          assertEquals(rsp, rsp.cookie("name", "value"));
        });
  }

  @Test
  public void download() throws Exception {
    File file = new File("file.ppt");
    new MockUnit(Response.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);

          rsp.download(file);
          rsp.download("alias", file);

          rsp.download("file.pdf");
          rsp.download("alias", "file.pdf");

          rsp.download(eq("file.pdf"), isA(InputStream.class));

        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));

          rsp.download(file);

          rsp.download("alias", file);

          rsp.download("file.pdf");

          rsp.download("alias", "file.pdf");

          rsp.download("file.pdf", new ByteArrayInputStream(new byte[0]));

        });
  }

  @Test
  public void charset() throws Exception {
    new MockUnit(Response.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);
          when(rsp.charset()).thenReturn(StandardCharsets.UTF_8);

          when(rsp.charset(StandardCharsets.US_ASCII)).thenReturn(null);
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));
          assertEquals(StandardCharsets.UTF_8, rsp.charset());

          assertEquals(rsp, rsp.charset(StandardCharsets.US_ASCII));
        });
  }

  @Test
  public void clearCookie() throws Exception {
    new MockUnit(Response.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);
          when(rsp.clearCookie("cookie")).thenReturn(null);
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));
          assertEquals(rsp, rsp.clearCookie("cookie"));
        });
  }

  @Test
  public void committed() throws Exception {
    new MockUnit(Response.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);
          when(rsp.committed()).thenReturn(true);
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));
          assertEquals(true, rsp.committed());
        });
  }

  @Test
  public void length() throws Exception {
    new MockUnit(Response.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);
          when(rsp.length(10)).thenReturn(null);
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));
          assertEquals(rsp, rsp.length(10));
        });
  }

  @Test
  public void redirect() throws Exception {
    new MockUnit(Response.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);
          rsp.redirect("/location");

          rsp.redirect(Status.MOVED_PERMANENTLY, "/location");
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));
          rsp.redirect("/location");

          rsp.redirect(Status.MOVED_PERMANENTLY, "/location");
        });
  }

  @Test
  public void send() throws Exception {
    Result body = Results.ok();
    Object obody = new Object();
    new MockUnit(Response.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);

          when(rsp.status()).thenReturn(Optional.empty());
          when(rsp.type()).thenReturn(Optional.empty());
        })
        .expect(unit -> {
          Response rsp = unit.get(Response.class);

          rsp.send(body);

          rsp.send(unit.capture(Result.class));
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));

          rsp.send(body);

          rsp.send(obody);
        });
  }

  @Test
  public void status() throws Exception {
    new MockUnit(Response.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);

          when(rsp.status()).thenReturn(Optional.empty());

          when(rsp.status(200)).thenReturn(rsp);
          when(rsp.status(Status.BAD_REQUEST)).thenReturn(rsp);
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));

          assertEquals(Optional.empty(), rsp.status());
          assertEquals(rsp, rsp.status(200));
          assertEquals(rsp, rsp.status(Status.BAD_REQUEST));
        });
  }

  @Test
  public void toStr() throws Exception {

    Response rsp = new Response.Forwarding(new ResponseTest.ResponseMock() {
      @Override
      public String toString() {
        return "something something dark";
      }
    });

    assertEquals("something something dark", rsp.toString());
  }

  @Test
  public void singleHeader() throws Exception {
    new MockUnit(Response.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);

          when(rsp.header("h", "v")).thenReturn(rsp);
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));

          assertEquals(rsp, rsp.header("h", "v"));
        });
  }

  @Test
  public void arrayHeader() throws Exception {
    new MockUnit(Response.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);

          when(rsp.header("h", "v1", 2)).thenReturn(rsp);
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));

          assertEquals(rsp, rsp.header("h", "v1", 2));
        });
  }

  @Test
  public void listHeader() throws Exception {
    new MockUnit(Response.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);

          when(rsp.header("h", new ArrayList<>(Arrays.<Object>asList("v1", 2)))).thenReturn(rsp);
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));

          assertEquals(rsp, rsp.header("h", new ArrayList<>(Arrays.<Object>asList("v1", 2))));
        });
  }

  @Test
  public void end() throws Exception {
    new MockUnit(Response.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);
          rsp.end();
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));

          rsp.end();
        });
  }

  @Test
  public void pushAfter() throws Exception {
    new MockUnit(Response.class, Route.After.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);
          rsp.after(unit.get(Route.After.class));
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));

          rsp.after(unit.get(Route.After.class));
        });
  }

  @Test
  public void pushComplete() throws Exception {
    new MockUnit(Response.class, Route.Complete.class)
        .expect(unit -> {
          Response rsp = unit.get(Response.class);
          rsp.complete(unit.get(Route.Complete.class));
        })
        .run(unit -> {
          Response rsp = new Response.Forwarding(unit.get(Response.class));
          rsp.complete(unit.get(Route.Complete.class));
        });
  }

}
