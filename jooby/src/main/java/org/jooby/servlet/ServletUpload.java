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

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.Part;

import org.jooby.spi.NativeUpload;

import com.google.common.collect.ImmutableList;

public class ServletUpload implements NativeUpload {

  private final Part upload;

  private final String tmpdir;

  private File file;

  public ServletUpload(final Part upload, final String tmpdir) {
    this.upload = requireNonNull(upload, "A part upload is required.");
    this.tmpdir = requireNonNull(tmpdir, "A tmpdir is required.");
  }

  @Override
  public void close() throws IOException {
    if (file != null) {
      file.delete();
    }
    upload.delete();
  }

  @Override
  public String name() {
    return upload.getSubmittedFileName();
  }

  @Override
  public List<String> headers(final String name) {
    Collection<String> headers = upload.getHeaders(name.toLowerCase());
    if (headers == null) {
      return Collections.emptyList();
    }
    return ImmutableList.copyOf(headers);
  }

  @Override
  public File file() throws IOException {
    if (file == null) {
      String name = "tmp-" + Long.toHexString(System.currentTimeMillis()) + "." + name();
      upload.write(name);
      file = new File(tmpdir, name);
    }
    return file;
  }

}
