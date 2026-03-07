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
package org.jooby.servlet;

import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.jooby.MediaType;
import org.jooby.test.MockUnit;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

public class ServletServletRequestTest {

  @Test
  public void defaults() throws IOException, Exception {
    String tmpdir = System.getProperty("java.io.tmpdir");
    new MockUnit(HttpServletRequest.class)
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          when(req.getContentType()).thenReturn("text/html");
          when(req.getPathInfo()).thenReturn("/");
          when(req.getContextPath()).thenReturn("");
        })
        .run(unit -> {
          new ServletServletRequest(unit.get(HttpServletRequest.class), tmpdir);
        });
  }

  @Test
  public void nullPathInfo() throws IOException, Exception {
    String tmpdir = System.getProperty("java.io.tmpdir");
    new MockUnit(HttpServletRequest.class)
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          when(req.getContentType()).thenReturn("text/html");
          when(req.getPathInfo()).thenReturn(null);
          when(req.getContextPath()).thenReturn("");
        })
        .run(unit -> {
          String path = new ServletServletRequest(unit.get(HttpServletRequest.class), tmpdir)
              .path();
          assertEquals("/", path);
        });
  }

  @Test
  public void withContextPath() throws IOException, Exception {
    String tmpdir = System.getProperty("java.io.tmpdir");
    new MockUnit(HttpServletRequest.class)
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          when(req.getContentType()).thenReturn("text/html");
          when(req.getPathInfo()).thenReturn(null);
          when(req.getContextPath()).thenReturn("/foo");
        })
        .run(unit -> {
          String path = new ServletServletRequest(unit.get(HttpServletRequest.class), tmpdir)
              .path();
          assertEquals("/foo/", path);
        });
  }

  @Test
  public void defaultsNullCT() throws IOException, Exception {
    String tmpdir = System.getProperty("java.io.tmpdir");
    new MockUnit(HttpServletRequest.class)
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          when(req.getContentType()).thenReturn(null);
          when(req.getPathInfo()).thenReturn("/");
          when(req.getContextPath()).thenReturn("");
        })
        .run(unit -> {
          new ServletServletRequest(unit.get(HttpServletRequest.class), tmpdir);
        });

  }

  @Test
  public void multipartDefaults() throws IOException, Exception {
    String tmpdir = System.getProperty("java.io.tmpdir");
    new MockUnit(HttpServletRequest.class)
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          when(req.getContentType()).thenReturn(MediaType.multipart.name());
          when(req.getPathInfo()).thenReturn("/");
          when(req.getContextPath()).thenReturn("");
        })
        .run(unit -> {
          new ServletServletRequest(unit.get(HttpServletRequest.class), tmpdir);
        });
  }

  @Test
  public void reqMethod() throws IOException, Exception {
    String tmpdir = System.getProperty("java.io.tmpdir");
    new MockUnit(HttpServletRequest.class)
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          when(req.getContentType()).thenReturn("text/html");
          when(req.getPathInfo()).thenReturn("/");
          when(req.getMethod()).thenReturn("GET");
          when(req.getContextPath()).thenReturn("");
        })
        .run(unit -> {
          assertEquals("GET", new ServletServletRequest(unit.get(HttpServletRequest.class),
              tmpdir).method());
        });

  }

  @Test
  public void path() throws IOException, Exception {
    String tmpdir = System.getProperty("java.io.tmpdir");
    new MockUnit(HttpServletRequest.class)
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          when(req.getContentType()).thenReturn("text/html");
          when(req.getPathInfo()).thenReturn("/spaces%20in%20it");
          when(req.getContextPath()).thenReturn("");
        })
        .run(unit -> {
          assertEquals("/spaces in it",
              new ServletServletRequest(unit.get(HttpServletRequest.class), tmpdir).path());
        });

  }

  @Test
  public void paramNames() throws IOException, Exception {
    String tmpdir = System.getProperty("java.io.tmpdir");
    new MockUnit(HttpServletRequest.class)
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          when(req.getContentType()).thenReturn("text/html");
          when(req.getPathInfo()).thenReturn("/");
          when(req.getParameterNames()).thenReturn(
              Iterators.asEnumeration(Lists.newArrayList("p1", "p2").iterator()));
          when(req.getContextPath()).thenReturn("");
        })
        .run(unit -> {
          assertEquals(Lists.newArrayList("p1", "p2"),
              new ServletServletRequest(unit.get(HttpServletRequest.class), tmpdir)
                  .paramNames());
        });

  }

  @Test
  public void params() throws IOException, Exception {
    String tmpdir = System.getProperty("java.io.tmpdir");
    new MockUnit(HttpServletRequest.class)
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          when(req.getContentType()).thenReturn("text/html");
          when(req.getPathInfo()).thenReturn("/");
          when(req.getParameterValues("x")).thenReturn(new String[]{"a", "b" });
          when(req.getContextPath()).thenReturn("");
        })
        .run(unit -> {
          assertEquals(Lists.newArrayList("a", "b"),
              new ServletServletRequest(unit.get(HttpServletRequest.class), tmpdir)
                  .params("x"));
        });

  }

  @Test
  public void noparams() throws IOException, Exception {
    String tmpdir = System.getProperty("java.io.tmpdir");
    new MockUnit(HttpServletRequest.class)
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          when(req.getContentType()).thenReturn("text/html");
          when(req.getPathInfo()).thenReturn("/");
          when(req.getParameterValues("x")).thenReturn(null);
          when(req.getContextPath()).thenReturn("");
        })
        .run(unit -> {
          assertEquals(Lists.newArrayList(),
              new ServletServletRequest(unit.get(HttpServletRequest.class), tmpdir)
                  .params("x"));
        });

  }

  @Test
  public void attributes() throws Exception {
    String tmpdir = System.getProperty("java.io.tmpdir");
    final UUID serverAttribute = UUID.randomUUID();
    new MockUnit(HttpServletRequest.class)
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          when(req.getContentType()).thenReturn("text/html");
          when(req.getPathInfo()).thenReturn("/");
          when(req.getContextPath()).thenReturn("");
          when(req.getAttributeNames()).thenReturn(
              Collections.enumeration(Collections.singletonList("server.attribute")));
          when(req.getAttribute("server.attribute")).thenReturn(serverAttribute);
        })
        .run(unit -> {
          assertEquals(ImmutableMap.of("server.attribute", serverAttribute),
              new ServletServletRequest(unit.get(HttpServletRequest.class), tmpdir)
                  .attributes());
        });

  }

  @Test
  public void emptyAttributes() throws Exception {
    String tmpdir = System.getProperty("java.io.tmpdir");
    new MockUnit(HttpServletRequest.class)
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          when(req.getContentType()).thenReturn("text/html");
          when(req.getPathInfo()).thenReturn("/");
          when(req.getContextPath()).thenReturn("");
          when(req.getAttributeNames()).thenReturn(Collections.emptyEnumeration());
        })
        .run(unit -> {
          assertEquals(Collections.emptyMap(),
              new ServletServletRequest(unit.get(HttpServletRequest.class), tmpdir)
                  .attributes());
        });

  }

  @Test(expected = IOException.class)
  public void filesFailure() throws IOException, Exception {
    String tmpdir = System.getProperty("java.io.tmpdir");
    new MockUnit(HttpServletRequest.class)
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          when(req.getContentType()).thenReturn(MediaType.multipart.name());
          when(req.getPathInfo()).thenReturn("/");
          when(req.getParts()).thenThrow(new ServletException("intentional err"));
          when(req.getContextPath()).thenReturn("");
        })
        .run(unit -> {
          new ServletServletRequest(unit.get(HttpServletRequest.class), tmpdir)
              .files("x");
        });

  }

  @Test(expected = UnsupportedOperationException.class)
  public void noupgrade() throws IOException, Exception {
    String tmpdir = System.getProperty("java.io.tmpdir");
    new MockUnit(HttpServletRequest.class)
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          when(req.getContentType()).thenReturn(MediaType.multipart.name());
          when(req.getPathInfo()).thenReturn("/");
          when(req.getContextPath()).thenReturn("");
        })
        .run(unit -> {
          assertEquals(Lists.newArrayList(),
              new ServletServletRequest(unit.get(HttpServletRequest.class), tmpdir)
                  .upgrade(ServletServletRequest.class));
        });

  }

}
