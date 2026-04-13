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

import com.google.common.base.Strings;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public interface AssetSource {
  URL getResource(String name);

  static AssetSource fromClassPath(ClassLoader loader, String source) {
    if (Strings.isNullOrEmpty(source) || "/".equals(source.trim())) {
      throw new IllegalArgumentException(
          "For security reasons root classpath access is not allowed: " + source);
    }
    return path -> {
      URL resource = loader.getResource(path);
      if (resource == null) {
        return null;
      }
      String realPath = resource.getPath();
      if (realPath.startsWith(source)) {
        return resource;
      }
      return null;
    };
  }

  static AssetSource fromFileSystem(Path basedir) {
    return name -> {
      Path path = basedir.resolve(name).normalize();
      if (Files.exists(path) && path.startsWith(basedir)) {
        try {
          return path.toUri().toURL();
        } catch (MalformedURLException x) {
          // shh
        }
      }
      return null;
    };
  }
}
