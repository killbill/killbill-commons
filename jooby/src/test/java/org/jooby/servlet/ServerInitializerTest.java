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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isA;
import org.jooby.Jooby;
import org.jooby.test.MockUnit;
import org.junit.Test;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;

public class ServerInitializerTest {

  @SuppressWarnings({"rawtypes", "unchecked" })
  @Test
  public void contextInitialized() throws Exception {
    new MockUnit(ServletContextEvent.class)
        .expect(unit -> {
          Class appClass = Jooby.class;
          String appClassname = appClass.getName();

          ClassLoader loader = unit.mock(ClassLoader.class);
          when(loader.loadClass(appClassname)).thenReturn(appClass);

          ServletContext ctx = unit.mock(ServletContext.class);
          when(ctx.getInitParameter("application.class")).thenReturn(appClassname);
          when(ctx.getClassLoader()).thenReturn(loader);
          when(ctx.getContextPath()).thenReturn("/");
          ctx.setAttribute(eq(Jooby.class.getName()), isA(Jooby.class));

          ServletContextEvent sce = unit.get(ServletContextEvent.class);
          when(sce.getServletContext()).thenReturn(ctx);
        })
        .run(unit -> {
          try {
          ServerInitializer initializer = new ServerInitializer();
          initializer.contextInitialized(unit.get(ServletContextEvent.class));
          } catch (Throwable ex) {
            ex.printStackTrace();
          }
        });
  }

  @SuppressWarnings({"rawtypes" })
  @Test(expected = ClassNotFoundException.class)
  public void contextInitializedShouldReThrowException() throws Exception {
    new MockUnit(ServletContextEvent.class)
        .expect(
            unit -> {
              Class appClass = Jooby.class;
              String appClassname = appClass.getName();

              ClassLoader loader = unit.mock(ClassLoader.class);
              when(loader.loadClass(appClassname)).thenThrow(
                  new ClassNotFoundException("intentional err"));

              ServletContext ctx = unit.mock(ServletContext.class);
              when(ctx.getInitParameter("application.class")).thenReturn(appClassname);
              when(ctx.getClassLoader()).thenReturn(loader);
              when(ctx.getContextPath()).thenReturn("/");
              ctx.setAttribute(eq(Jooby.class.getName()), isA(Jooby.class));

              ServletContextEvent sce = unit.get(ServletContextEvent.class);
              when(sce.getServletContext()).thenReturn(ctx);
            })
        .run(unit -> {
          ServerInitializer initializer = new ServerInitializer();
          initializer.contextInitialized(unit.get(ServletContextEvent.class));
        });
  }

  @SuppressWarnings({"rawtypes" })
  @Test
  public void contextDestroyed() throws Exception {
    new MockUnit(ServletContextEvent.class)
        .expect(unit -> {
          Class appClass = Jooby.class;
          String appClassname = appClass.getName();

          Jooby app = unit.mock(Jooby.class);
          app.stop();

          ServletContext ctx = unit.mock(ServletContext.class);
          when(ctx.getAttribute(appClassname)).thenReturn(app);

          ServletContextEvent sce = unit.get(ServletContextEvent.class);
          when(sce.getServletContext()).thenReturn(ctx);
        })
        .run(unit -> {
          new ServerInitializer().contextDestroyed(unit.get(ServletContextEvent.class));
        });
  }

  @SuppressWarnings({"rawtypes" })
  @Test
  public void contextDestroyedShouldIgnoreMissingAttr() throws Exception {
    new MockUnit(ServletContextEvent.class)
        .expect(unit -> {
          Class appClass = Jooby.class;
          String appClassname = appClass.getName();

          ServletContext ctx = unit.mock(ServletContext.class);
          when(ctx.getAttribute(appClassname)).thenReturn(null);

          ServletContextEvent sce = unit.get(ServletContextEvent.class);
          when(sce.getServletContext()).thenReturn(ctx);
        })
        .run(unit -> {
          new ServerInitializer().contextDestroyed(unit.get(ServletContextEvent.class));
        });
  }
}
