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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.typesafe.config.Config;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.Test;

public class FileConfTest {

  @Test
  public void rootFile() throws Exception {
    Path userDir = Files.createTempDirectory("jooby-fileconf-root");
    try {
      writeFile(userDir.resolve("app.conf"), "source = root");

      Config conf = withUserDir(userDir, () -> Jooby.fileConfig("app.conf"));

      assertEquals("root", conf.getString("source"));
    } finally {
      deleteRecursively(userDir);
    }
  }

  @Test
  public void confFile() throws Exception {
    Path userDir = Files.createTempDirectory("jooby-fileconf-conf");
    try {
      Path confDir = Files.createDirectories(userDir.resolve("conf"));
      writeFile(confDir.resolve("app.conf"), "source = conf");

      Config conf = withUserDir(userDir, () -> Jooby.fileConfig("app.conf"));

      assertEquals("conf", conf.getString("source"));
    } finally {
      deleteRecursively(userDir);
    }
  }

  @Test
  public void empty() throws Exception {
    Path userDir = Files.createTempDirectory("jooby-fileconf-empty");
    try {
      Config conf = withUserDir(userDir, () -> Jooby.fileConfig("app.conf"));

      assertTrue(conf.entrySet().isEmpty());
    } finally {
      deleteRecursively(userDir);
    }
  }

  private void writeFile(final Path path, final String content) throws IOException {
    Files.write(path, content.getBytes(StandardCharsets.UTF_8));
  }

  private Config withUserDir(final Path userDir, final ConfigSupplier supplier) throws Exception {
    String original = System.getProperty("user.dir");
    System.setProperty("user.dir", userDir.toString());
    try {
      return supplier.get();
    } finally {
      if (original == null) {
        System.clearProperty("user.dir");
      } else {
        System.setProperty("user.dir", original);
      }
    }
  }

  private void deleteRecursively(final Path root) throws IOException {
    Files.walk(root)
        .sorted(Comparator.reverseOrder())
        .forEach(path -> {
          try {
            Files.deleteIfExists(path);
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        });
  }

  @FunctionalInterface
  private interface ConfigSupplier {
    Config get() throws Exception;
  }
}
