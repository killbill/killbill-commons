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

import static org.easymock.EasyMock.expect;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jooby.Jooby;
import org.jooby.spi.HttpHandler;
import org.jooby.test.MockUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.typesafe.config.Config;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ServletHandler.class, ServletServletRequest.class, ServletServletResponse.class })
public class ServletHandlerTest {

  MockUnit.Block init = unit -> {
    HttpHandler dispatcher = unit.get(HttpHandler.class);

    Config config = unit.mock(Config.class);
    expect(config.getString("application.tmpdir")).andReturn("target");

    Jooby app = unit.mock(Jooby.class);
    expect(app.require(HttpHandler.class)).andReturn(dispatcher);
    expect(app.require(Config.class)).andReturn(config);

    ServletContext ctx = unit.mock(ServletContext.class);
    expect(ctx.getAttribute(Jooby.class.getName())).andReturn(app);

    ServletConfig servletConfig = unit.get(ServletConfig.class);

    expect(servletConfig.getServletContext()).andReturn(ctx);
  };

  @Test
  public void initMethodMustAskForDependencies() throws Exception {
    new MockUnit(ServletConfig.class, HttpHandler.class)
        .expect(init)
        .run(unit ->
            new ServletHandler()
                .init(unit.get(ServletConfig.class))
        );
  }

  @Test
  public void serviceShouldDispatchToHandler() throws Exception {
    new MockUnit(ServletConfig.class, HttpHandler.class, HttpServletRequest.class,
        HttpServletResponse.class)
        .expect(init)
        .expect(
            unit -> {
              HttpHandler dispatcher = unit.get(HttpHandler.class);

              ServletServletRequest req = unit.mockConstructor(ServletServletRequest.class,
                  new Class[]{HttpServletRequest.class, String.class },
                  unit.get(HttpServletRequest.class), "target");
              ServletServletResponse rsp = unit.mockConstructor(ServletServletResponse.class,
                  new Class[]{HttpServletRequest.class, HttpServletResponse.class },
                  unit.get(HttpServletRequest.class), unit.get(HttpServletResponse.class));

              dispatcher.handle(req, rsp);
            })
        .run(unit -> {
          ServletHandler handler = new ServletHandler();
          handler.init(unit.get(ServletConfig.class));
          handler.service(unit.get(HttpServletRequest.class), unit.get(HttpServletResponse.class));
        });
  }

  @Test(expected = IllegalStateException.class)
  public void serviceShouldCatchExceptionAndRethrowAsRuntime() throws Exception {
    HttpHandler dispatcher = (request, response) -> {
      throw new Exception("intentional err");
    };

    new MockUnit(ServletConfig.class, HttpServletRequest.class,
        HttpServletResponse.class)
        .expect(unit -> {
          Config config = unit.mock(Config.class);
          expect(config.getString("application.tmpdir")).andReturn("target");

          Jooby app = unit.mock(Jooby.class);
          expect(app.require(HttpHandler.class)).andReturn(dispatcher);
          expect(app.require(Config.class)).andReturn(config);

          ServletContext ctx = unit.mock(ServletContext.class);
          expect(ctx.getAttribute(Jooby.class.getName())).andReturn(app);

          ServletConfig servletConfig = unit.get(ServletConfig.class);

          expect(servletConfig.getServletContext()).andReturn(ctx);
        })
        .expect(unit -> {
          unit.mockConstructor(ServletServletRequest.class,
              new Class[]{HttpServletRequest.class, String.class },
              unit.get(HttpServletRequest.class), "target");
          unit.mockConstructor(ServletServletResponse.class,
              new Class[]{HttpServletRequest.class, HttpServletResponse.class },
              unit.get(HttpServletRequest.class), unit.get(HttpServletResponse.class));
        })
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          expect(req.getRequestURI()).andReturn("/");
        })
        .run(unit -> {
          ServletHandler handler = new ServletHandler();
          handler.init(unit.get(ServletConfig.class));
          handler.service(unit.get(HttpServletRequest.class), unit.get(HttpServletResponse.class));
        });
  }

  @Test(expected = IOException.class)
  public void serviceShouldCatchIOExceptionAndRethrow() throws Exception {
    HttpHandler dispatcher = (request, response) -> {
      throw new IOException("intentional err");
    };

    new MockUnit(ServletConfig.class, HttpServletRequest.class,
        HttpServletResponse.class)
        .expect(unit -> {
          Config config = unit.mock(Config.class);
          expect(config.getString("application.tmpdir")).andReturn("target");

          Jooby app = unit.mock(Jooby.class);
          expect(app.require(HttpHandler.class)).andReturn(dispatcher);
          expect(app.require(Config.class)).andReturn(config);

          ServletContext ctx = unit.mock(ServletContext.class);
          expect(ctx.getAttribute(Jooby.class.getName())).andReturn(app);

          ServletConfig servletConfig = unit.get(ServletConfig.class);

          expect(servletConfig.getServletContext()).andReturn(ctx);
        })
        .expect(unit -> {
          unit.mockConstructor(ServletServletRequest.class,
              new Class[]{HttpServletRequest.class, String.class },
              unit.get(HttpServletRequest.class), "target");
          unit.mockConstructor(ServletServletResponse.class,
              new Class[]{HttpServletRequest.class, HttpServletResponse.class },
              unit.get(HttpServletRequest.class), unit.get(HttpServletResponse.class));
        })
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          expect(req.getRequestURI()).andReturn("/");
        })
        .run(unit -> {
          ServletHandler handler = new ServletHandler();
          handler.init(unit.get(ServletConfig.class));
          handler.service(unit.get(HttpServletRequest.class), unit.get(HttpServletResponse.class));
        });
  }

  @Test(expected = ServletException.class)
  public void serviceShouldCatchServletExceptionAndRethrow() throws Exception {
    HttpHandler dispatcher = (request, response) -> {
      throw new ServletException("intentional err");
    };

    new MockUnit(ServletConfig.class, HttpServletRequest.class,
        HttpServletResponse.class)
        .expect(unit -> {
          Config config = unit.mock(Config.class);
          expect(config.getString("application.tmpdir")).andReturn("target");

          Jooby app = unit.mock(Jooby.class);
          expect(app.require(HttpHandler.class)).andReturn(dispatcher);
          expect(app.require(Config.class)).andReturn(config);

          ServletContext ctx = unit.mock(ServletContext.class);
          expect(ctx.getAttribute(Jooby.class.getName())).andReturn(app);

          ServletConfig servletConfig = unit.get(ServletConfig.class);

          expect(servletConfig.getServletContext()).andReturn(ctx);
        })
        .expect(unit -> {
          unit.mockConstructor(ServletServletRequest.class,
              new Class[]{HttpServletRequest.class, String.class },
              unit.get(HttpServletRequest.class), "target");
          unit.mockConstructor(ServletServletResponse.class,
              new Class[]{HttpServletRequest.class, HttpServletResponse.class },
              unit.get(HttpServletRequest.class), unit.get(HttpServletResponse.class));
        })
        .expect(unit -> {
          HttpServletRequest req = unit.get(HttpServletRequest.class);
          expect(req.getRequestURI()).andReturn("/");
        })
        .run(unit -> {
          ServletHandler handler = new ServletHandler();
          handler.init(unit.get(ServletConfig.class));
          handler.service(unit.get(HttpServletRequest.class), unit.get(HttpServletResponse.class));
        });
  }
}
