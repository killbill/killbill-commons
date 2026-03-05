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

import java.io.File;

import org.jooby.test.MockUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Jooby.class, File.class, ConfigFactory.class })
public class FileConfTest {

  @Test
  public void rootFile() throws Exception {
    Config conf = ConfigFactory.empty();
    new MockUnit()
        .expect(unit -> {
          unit.mockStatic(ConfigFactory.class);
        })
        .expect(unit -> {
          File dir = unit.constructor(File.class)
              .args(String.class)
              .build(System.getProperty("user.dir"));

          File root = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "app.conf");
          expect(root.exists()).andReturn(true);

          expect(ConfigFactory.parseFile(root)).andReturn(conf);
        })
        .run(unit -> {
          assertEquals(conf, Jooby.fileConfig("app.conf"));
        });
  }

  @Test
  public void confFile() throws Exception {
    Config conf = ConfigFactory.empty();
    new MockUnit()
        .expect(unit -> {
          unit.mockStatic(ConfigFactory.class);
        })
        .expect(unit -> {
          File dir = unit.constructor(File.class)
              .args(String.class)
              .build(System.getProperty("user.dir"));

          File root = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "app.conf");
          expect(root.exists()).andReturn(false);

          File cdir = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "conf");

          File cfile = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(cdir, "app.conf");
          expect(cfile.exists()).andReturn(true);

          expect(ConfigFactory.parseFile(cfile)).andReturn(conf);
        })
        .run(unit -> {
          assertEquals(conf, Jooby.fileConfig("app.conf"));
        });
  }

  @Test
  public void empty() throws Exception {
    Config conf = ConfigFactory.empty();
    new MockUnit()
        .expect(unit -> {
          unit.mockStatic(ConfigFactory.class);
        })
        .expect(unit -> {
          File dir = unit.constructor(File.class)
              .args(String.class)
              .build(System.getProperty("user.dir"));

          File root = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "app.conf");
          expect(root.exists()).andReturn(false);

          File cdir = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(dir, "conf");

          File cfile = unit.constructor(File.class)
              .args(File.class, String.class)
              .build(cdir, "app.conf");
          expect(cfile.exists()).andReturn(false);

          expect(ConfigFactory.empty()).andReturn(conf);
        })
        .run(unit -> {
          assertEquals(conf, Jooby.fileConfig("app.conf"));
        });
  }

}
