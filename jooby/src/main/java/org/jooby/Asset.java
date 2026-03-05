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

import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Longs;
import org.jooby.funzy.Throwing;

import javax.annotation.Nonnull;

/**
 * Usually a public file/resource like javascript, css, images files, etc...
 * An asset consist of content type, stream and last modified since attributes, between others.
 *
 * @author edgar
 * @since 0.1.0
 * @see Jooby#assets(String)
 */
public interface Asset {

  /**
   * Forwarding asset.
   *
   * @author edgar
   */
  public class Forwarding implements Asset {

    private Asset asset;

    public Forwarding(final Asset asset) {
      this.asset = requireNonNull(asset, "Asset is required.");
    }

    @Override
    public String etag() {
      return asset.etag();
    }

    @Override
    public String name() {
      return asset.name();
    }

    @Override
    public String path() {
      return asset.path();
    }

    @Override
    public URL resource() {
      return asset.resource();
    }

    @Override
    public long length() {
      return asset.length();
    }

    @Override
    public long lastModified() {
      return asset.lastModified();
    }

    @Override
    public InputStream stream() throws Exception {
      return asset.stream();
    }

    @Override
    public MediaType type() {
      return asset.type();
    }

  }

  /**
   * Examples:
   *
   * <pre>
   *  GET /assets/index.js {@literal ->} index.js
   *  GET /assets/js/index.js {@literal ->} index.js
   * </pre>
   *
   * @return The asset name (without path).
   */
  @Nonnull
  default String name() {
    String path = path();
    int slash = path.lastIndexOf('/');
    return path.substring(slash + 1);
  }

  /**
   * Examples:
   *
   * <pre>
   *  GET /assets/index.js {@literal ->} /assets/index.js
   *  GET /assets/js/index.js {@literal ->} /assets/js/index.js
   * </pre>
   *
   * @return The asset requested path, includes the name.
   */
  @Nonnull
  String path();

  /**
   * @return URL representing the resource.
   */
  @Nonnull
  URL resource();

  /**
   * @return Generate a weak Etag using the {@link #path()}, {@link #lastModified()} and
   *         {@link #length()}.
   */
  @Nonnull
  default String etag() {
    try {
      StringBuilder b = new StringBuilder(32);
      b.append("W/\"");

      BaseEncoding b64 = BaseEncoding.base64();
      int lhash = resource().toURI().hashCode();

      b.append(b64.encode(Longs.toByteArray(lastModified() ^ lhash)));
      b.append(b64.encode(Longs.toByteArray(length() ^ lhash)));
      b.append('"');
      return b.toString();
    } catch (URISyntaxException x) {
      throw Throwing.sneakyThrow(x);
    }
  }

  /**
   * @return Asset size (in bytes) or <code>-1</code> if undefined.
   */
  long length();

  /**
   * @return The last modified date if possible or -1 when isn't.
   */
  long lastModified();

  /**
   * @return The content of this asset.
   * @throws Exception If content can't be read it.
   */
  @Nonnull
  InputStream stream() throws Exception;

  /**
   * @return Asset media type.
   */
  @Nonnull
  MediaType type();
}
