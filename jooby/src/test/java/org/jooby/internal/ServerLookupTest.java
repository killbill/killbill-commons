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

import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;

import org.jooby.Env;
import org.jooby.Jooby;
import org.jooby.spi.Server;
import org.jooby.test.MockUnit;
import org.junit.Test;

import com.google.inject.Binder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ServerLookupTest {

  private static int calls = 0;

  public static class ServerModule implements Jooby.Module {

    @Override
    public void configure(final Env env, final Config config, final Binder binder) {
      calls += 1;
    }

  }

  @Test
  public void configure() throws Exception {
    calls = 0;
    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(unit -> {
          Config config = unit.get(Config.class);
          when(config.hasPath("server.module")).thenReturn(true);
          when(config.getString("server.module")).thenReturn(ServerModule.class.getName());
        })
        .run(unit -> {
          new ServerLookup()
              .configure(unit.get(Env.class), unit.get(Config.class), unit.get(Binder.class));
          assertEquals(1, calls);
        });
  }

  @Test
  public void doNothingIfPropertyIsMissing() throws Exception {
    calls = 0;
    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(unit -> {
          Config config = unit.get(Config.class);
          when(config.hasPath("server.module")).thenReturn(false);
        })
        .run(unit -> {
          new ServerLookup()
              .configure(unit.get(Env.class), unit.get(Config.class), unit.get(Binder.class));
          assertEquals(0, calls);
        });
  }

  @Test(expected = ClassNotFoundException.class)
  public void failOnBadServerName() throws Exception {
    calls = 0;
    new MockUnit(Env.class, Config.class, Binder.class)
        .expect(unit -> {
          Config config = unit.get(Config.class);
          when(config.hasPath("server.module")).thenReturn(true);
          when(config.getString("server.module")).thenReturn("org.Missing");
        })
        .run(unit -> {
          new ServerLookup()
              .configure(unit.get(Env.class), unit.get(Config.class), unit.get(Binder.class));
          assertEquals(0, calls);
        });
  }

  @Test
  public void config() throws Exception {
    new MockUnit(Config.class)
        .expect(unit -> {
          Config serverLookup = unit.mock(Config.class);

          Config defs = unit.mock(Config.class);
          when(serverLookup.withFallback(defs)).thenReturn(unit.get(Config.class));

          unit.mockStatic(ConfigFactory.class).when(() -> ConfigFactory.parseResources(Server.class, "server-defaults.conf"))
              .thenReturn(defs);

          unit.mockStatic(ConfigFactory.class).when(() -> ConfigFactory.parseResources(Server.class, "server.conf"))
              .thenReturn(serverLookup);
        })
        .run(unit -> {
          assertEquals(unit.get(Config.class), new ServerLookup().config());
        });
  }
}
