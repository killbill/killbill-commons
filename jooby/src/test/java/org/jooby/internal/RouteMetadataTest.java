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
package org.jooby.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;

import org.jooby.Env;
import org.jooby.test.MockUnit;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

import org.killbill.commons.utils.io.Resources;
import com.typesafe.config.Config;

public class RouteMetadataTest {

  public static class Mvc {

    public Mvc() {
    }

    public Mvc(final String s) {
    }

    public void noarg() {

    }

    public void arg(final double v) {

    }

    public void arg(final String x) {

    }

    public void arg(final double v, final int u) {

    }

    public static void staticMethod() {

    }
  }

  @Test
  public void noargconst() throws Exception {
    new MockUnit(Config.class)
        .expect(unit -> {
          Config config = unit.get(Config.class);
          when(config.hasPath("application.env")).thenReturn(true);
          when(config.getString("application.env")).thenReturn("dev");
        })
        .run(unit -> {
          Constructor<?> constructor = Mvc.class.getDeclaredConstructor();
          RouteMetadata ci = new RouteMetadata(Env.DEFAULT.build(unit.get(Config.class)));
          assertArrayEquals(new String[0], ci.names(constructor));
          assertEquals(45, ci.startAt(constructor));
        });
  }

  @Test
  public void consArgS() throws Exception {
    new MockUnit(Config.class)
        .expect(unit -> {
          Config config = unit.get(Config.class);
          when(config.hasPath("application.env")).thenReturn(true);
          when(config.getString("application.env")).thenReturn("dev");
        })
        .run(unit -> {
          Constructor<?> constructor = Mvc.class.getDeclaredConstructor(String.class);
          RouteMetadata ci = new RouteMetadata(Env.DEFAULT.build(unit.get(Config.class)));
          assertArrayEquals(new String[]{"s" }, ci.names(constructor));
          assertEquals(48, ci.startAt(constructor));
        });
  }

  @Test
  public void noargmethod() throws Exception {
    new MockUnit(Config.class)
        .expect(unit -> {
          Config config = unit.get(Config.class);
          when(config.hasPath("application.env")).thenReturn(true);
          when(config.getString("application.env")).thenReturn("dev");
        })
        .run(unit -> {
          Method m = Mvc.class.getDeclaredMethod("noarg");
          RouteMetadata ci = new RouteMetadata(Env.DEFAULT.build(unit.get(Config.class)));
          assertArrayEquals(new String[0], ci.names(m));
          assertEquals(53, ci.startAt(m));
        });
  }

  @Test
  public void argI() throws Exception {
    new MockUnit(Config.class)
        .expect(unit -> {
          Config config = unit.get(Config.class);
          when(config.hasPath("application.env")).thenReturn(true);
          when(config.getString("application.env")).thenReturn("dev");
        })
        .run(unit -> {
          Method m = Mvc.class.getDeclaredMethod("arg", double.class);
          RouteMetadata ci = new RouteMetadata(Env.DEFAULT.build(unit.get(Config.class)));
          assertArrayEquals(new String[]{"v" }, ci.names(m));
          assertEquals(57, ci.startAt(m));
        });
  }

  @Test
  public void argS() throws Exception {
    new MockUnit(Config.class)
        .expect(unit -> {
          Config config = unit.get(Config.class);
          when(config.hasPath("application.env")).thenReturn(true);
          when(config.getString("application.env")).thenReturn("dev");
        })
        .run(unit -> {
          Method m = Mvc.class.getDeclaredMethod("arg", String.class);
          RouteMetadata ci = new RouteMetadata(Env.DEFAULT.build(unit.get(Config.class)));
          assertArrayEquals(new String[]{"x" }, ci.names(m));
          assertEquals(61, ci.startAt(m));
        });
  }

  @Test
  public void argVU() throws Exception {
    new MockUnit(Config.class)
        .expect(unit -> {
          Config config = unit.get(Config.class);
          when(config.hasPath("application.env")).thenReturn(true);
          when(config.getString("application.env")).thenReturn("dev");
        })
        .run(unit -> {
          Method m = Mvc.class.getDeclaredMethod("arg", double.class, int.class);
          RouteMetadata ci = new RouteMetadata(Env.DEFAULT.build(unit.get(Config.class)));
          assertArrayEquals(new String[]{"v", "u" }, ci.names(m));
          assertEquals(65, ci.startAt(m));
        });
  }

  @Test
  public void nocache() throws Exception {
    new MockUnit(Config.class)
        .expect(unit -> {
          Config config = unit.get(Config.class);
          when(config.hasPath("application.env")).thenReturn(true);
          when(config.getString("application.env")).thenReturn("dev");
        })
        .run(unit -> {
          Method m = Mvc.class.getDeclaredMethod("arg", String.class);
          RouteMetadata ci = new RouteMetadata(Env.DEFAULT.build(unit.get(Config.class)));
          String[] params1 = ci.names(m);
          String[] params2 = ci.names(m);
          assertNotSame(params1, params2);
        });
  }

  @Test
  public void nocacheMavenBuild() throws Exception {
    InputStream stream = getClass().getResourceAsStream("RouteMetadataTest$Mvc.bc");
    new MockUnit(Config.class)
        .expect(unit -> {
          Config config = unit.get(Config.class);
          when(config.hasPath("application.env")).thenReturn(true);
          when(config.getString("application.env")).thenReturn("dev");
        })
        .expect(unit -> {
          URL resource = unit.mock(URL.class);
          when(resource.openStream()).thenReturn(stream);
          unit.mockStatic(Resources.class).when(() -> Resources.getResource(Mvc.class, "RouteMetadataTest$Mvc.class"))
              .thenReturn(resource);
        })
        .run(unit -> {
          Method method = Mvc.class.getDeclaredMethod("arg", String.class);
          RouteMetadata ci = new RouteMetadata(Env.DEFAULT.build(unit.get(Config.class)));
          String[] params = ci.names(method);
          assertEquals("x", params[0]);
        });
  }

  @Test(expected = IllegalStateException.class)
  public void cannotReadByteCode() throws Exception {
    new MockUnit(Config.class)
        .expect(unit -> {
          Config config = unit.get(Config.class);
          when(config.hasPath("application.env")).thenReturn(true);
          when(config.getString("application.env")).thenReturn("dev");
        })
        .expect(unit -> {
          InputStream stream = unit.mock(InputStream.class);
          URL resource = unit.mock(URL.class);
          when(resource.openStream()).thenReturn(stream);

          ClassReader reader = unit
              .mockConstructor(ClassReader.class, new Class[]{InputStream.class }, stream);
          doThrow(new NullPointerException("intentional err")).when(reader).accept(isA(ClassVisitor.class), eq(0));

          unit.mockStatic(Resources.class).when(() -> Resources.getResource(Mvc.class, "RouteMetadataTest$Mvc.class"))
              .thenReturn(resource);
        })
        .run(unit -> {
          Method method = Mvc.class.getDeclaredMethod("arg", String.class);
          RouteMetadata ci = new RouteMetadata(Env.DEFAULT.build(unit.get(Config.class)));
          String[] params = ci.names(method);
          assertEquals("x", params[0]);
        });
  }

  @Test
  public void withcache() throws Exception {
    new MockUnit(Config.class)
        .expect(unit -> {
          Config config = unit.get(Config.class);
          when(config.hasPath("application.env")).thenReturn(true);
          when(config.getString("application.env")).thenReturn("prod");
        })
        .run(unit -> {
          Method m = Mvc.class.getDeclaredMethod("arg", String.class);
          RouteMetadata ci = new RouteMetadata(Env.DEFAULT.build(unit.get(Config.class)));
          String[] params1 = ci.names(m);
          String[] params2 = ci.names(m);
          assertSame(params1, params2);
        });
  }

}
