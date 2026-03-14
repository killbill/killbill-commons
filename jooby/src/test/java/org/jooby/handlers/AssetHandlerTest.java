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
package org.jooby.handlers;

import org.jooby.Route;
import org.jooby.test.MockUnit;
import org.jooby.test.MockUnit.Block;
import org.junit.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.Mockito.when;
import static org.junit.Assert.assertNotNull;

public class AssetHandlerTest {

  @Test
  public void customClassloader() throws Exception {
    URI uri = Paths.get("src", "test", "resources", "org", "jooby").toUri();
    new MockUnit(ClassLoader.class)
        .expect(publicDir(uri, "JoobyTest.js"))
        .run(unit -> {
          URL value = newHandler(unit, "/")
              .resolve("JoobyTest.js");
          assertNotNull(value);
        });
  }

  private AssetHandler newHandler(MockUnit unit, String location) {
    AssetHandler handler = new AssetHandler(location, unit.get(ClassLoader.class));
    new Route.AssetDefinition("GET", "/assets/**", handler, false);
    return handler;
  }

  @Test
  public void shouldCallParentOnMissing() throws Exception {
    URI uri = Paths.get("src", "test", "resources", "org", "jooby").toUri();
    new MockUnit(ClassLoader.class)
        .expect(publicDir(uri, "assets/index.js", false))
        .expect(unit -> {
          ClassLoader loader = unit.get(ClassLoader.class);
          when(loader.getResource("assets/index.js")).thenReturn(uri.toURL());
        })
        .run(unit -> {
          URL value = newHandler(unit, "/")
              .resolve("assets/index.js");
          assertNotNull(value);
        });
  }

  @Test
  public void ignoreMalformedURL() throws Exception {
    Path path = Paths.get("src", "test", "resources", "org", "jooby");
    new MockUnit(ClassLoader.class, URI.class)
        .expect(publicDir(null, "assets/index.js"))
        .expect(unit -> {
          URI uri = unit.get(URI.class);
          when(uri.toURL()).thenThrow(new MalformedURLException());
        })
        .expect(unit -> {
          ClassLoader loader = unit.get(ClassLoader.class);
          when(loader.getResource("assets/index.js")).thenReturn(path.toUri().toURL());
        })
        .run(unit -> {
          URL value = newHandler(unit, "/")
              .resolve("assets/index.js");
          assertNotNull(value);
        });
  }

  private Block publicDir(final URI uri, final String name) {
    return publicDir(uri, name, true);
  }

  private Block publicDir(final URI uri, final String name, final boolean exists) {
    return unit -> {
      unit.mockStatic(Paths.class);

      Path basedir = unit.mock(Path.class);

      unit.mockStatic(Paths.class).when(() -> Paths.get("public")).thenReturn(basedir);

      Path path = unit.mock(Path.class);
      when(basedir.resolve(name)).thenReturn(path);
      when(path.normalize()).thenReturn(path);

      if (exists) {
        when(path.startsWith(basedir)).thenReturn(true);
      }

      unit.mockStatic(Files.class);
      unit.mockStatic(Files.class).when(() -> Files.exists(basedir)).thenReturn(true);
      unit.mockStatic(Files.class).when(() -> Files.exists(path)).thenReturn(exists);

      if (exists) {
        if (uri != null) {
          when(path.toUri()).thenReturn(uri);
        } else {
          when(path.toUri()).thenReturn(unit.get(URI.class));
        }
      }
    };
  }

}
