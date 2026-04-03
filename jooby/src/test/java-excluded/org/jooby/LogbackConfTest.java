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

import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;

import java.io.File;

import org.jooby.test.MockUnit;
import org.jooby.test.MockUnit.Block;
import org.junit.Test;

import com.typesafe.config.Config;

public class LogbackConfTest {

  @Test
  public void withConfigFile() throws Exception {
    new MockUnit(Config.class)
        .expect(conflog(true))
        .expect(unit -> {
          Config config = unit.get(Config.class);
          when(config.getString("logback.configurationFile")).thenReturn("logback.xml");
        })
        .run(unit -> {
          assertEquals("logback.xml", Jooby.logback(unit.get(Config.class)));
        });
  }

  @Test
  public void rootFile() throws Exception {
    new MockUnit(Config.class)
        .expect(conflog(false))
        .expect(env(null))
        .expect(unit -> {
          File dir = unit.constructor(File.class)
              .args(String.class)
              .build(System.getProperty("user.dir"));

          File conf = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "conf");

          File rlogback = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "logback.xml");
          when(rlogback.exists()).thenReturn(false);

          File clogback = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(conf, "logback.xml");
          when(clogback.exists()).thenReturn(false);
        })
        .run(unit -> {
          assertEquals("logback.xml", Jooby.logback(unit.get(Config.class)));
        });
  }

  @Test
  public void rootFileFound() throws Exception {
    new MockUnit(Config.class)
        .expect(conflog(false))
        .expect(env(null))
        .expect(unit -> {
          File dir = unit.constructor(File.class)
              .args(String.class)
              .build(System.getProperty("user.dir"));

          File conf = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "conf");

          File rlogback = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "logback.xml");
          when(rlogback.exists()).thenReturn(true);
          when(rlogback.getAbsolutePath()).thenReturn("foo/logback.xml");

          unit.constructor(File.class)
              .args(File.class, String.class)
              .build(conf, "logback.xml");
        })
        .run(unit -> {
          assertEquals("foo/logback.xml", Jooby.logback(unit.get(Config.class)));
        });
  }

  @Test
  public void confFile() throws Exception {
    new MockUnit(Config.class)
        .expect(conflog(false))
        .expect(env("foo"))
        .expect(unit -> {
          File dir = unit.constructor(File.class)
              .args(String.class)
              .build(System.getProperty("user.dir"));

          File conf = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "conf");

          File relogback = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "logback.foo.xml");
          when(relogback.exists()).thenReturn(false);

          File rlogback = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "logback.xml");
          when(rlogback.exists()).thenReturn(false);

          File clogback = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(conf, "logback.xml");
          when(clogback.exists()).thenReturn(false);

          File celogback = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(conf, "logback.foo.xml");
          when(celogback.exists()).thenReturn(false);
        })
        .run(unit -> {
          assertEquals("logback.xml", Jooby.logback(unit.get(Config.class)));
        });
  }

  @Test
  public void confFileFound() throws Exception {
    new MockUnit(Config.class)
        .expect(conflog(false))
        .expect(env("foo"))
        .expect(unit -> {
          File dir = unit.constructor(File.class)
              .args(String.class)
              .build(System.getProperty("user.dir"));

          File conf = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "conf");

          File relogback = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "logback.foo.xml");
          when(relogback.exists()).thenReturn(false);

          unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "logback.xml");

          File celogback = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(conf, "logback.foo.xml");
          when(celogback.exists()).thenReturn(true);
          when(celogback.getAbsolutePath()).thenReturn("logback.foo.xml");

          unit.constructor(File.class)
              .args(File.class, String.class)
              .build(conf, "logback.xml");
        })
        .run(unit -> {
          assertEquals("logback.foo.xml", Jooby.logback(unit.get(Config.class)));
        });
  }

  private Block env(final String env) {
    return unit -> {
      Config config = unit.get(Config.class);
      when(config.hasPath("application.env")).thenReturn(env != null);
      if (env != null) {
        when(config.getString("application.env")).thenReturn(env);
      }
    };
  }

  private Block conflog(final boolean b) {
    return unit -> {
      Config config = unit.get(Config.class);
      when(config.hasPath("logback.configurationFile")).thenReturn(b);
    };
  }

}
