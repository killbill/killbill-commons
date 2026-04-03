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

import com.google.inject.Binder;
import com.typesafe.config.Config;
import static java.util.Objects.requireNonNull;
import org.jooby.Env;
import org.jooby.Jooby;
import org.jooby.funzy.Throwing;
import org.jooby.spi.Server;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class ServerInitializer implements ServletContextListener {

  public static class ServletModule implements Jooby.Module {

    @Override
    public void configure(final Env env, final Config config, final Binder binder) {
      binder.bind(Server.class).toInstance(ServletContainer.NOOP);
    }

  }

  @Override
  public void contextInitialized(final ServletContextEvent sce) {
    ServletContext ctx = sce.getServletContext();
    String appClass = ctx.getInitParameter("application.class");
    requireNonNull(appClass, "Context param NOT found: application.class");

    Jooby.run(Throwing.throwingSupplier(() -> {
      Jooby app = (Jooby) ctx.getClassLoader().loadClass(appClass).newInstance();
      ctx.setAttribute(Jooby.class.getName(), app);
      return app;
    }), "application.path=" + ctx.getContextPath(), "server.module=" + ServletModule.class.getName());
  }

  @Override
  public void contextDestroyed(final ServletContextEvent sce) {
    ServletContext ctx = sce.getServletContext();
    Jooby app = (Jooby) ctx.getAttribute(Jooby.class.getName());
    if (app != null) {
      app.stop();
    }
  }

}
