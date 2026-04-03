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
package jetty;

import com.google.common.io.ByteStreams;
import org.jooby.Jooby;
import org.jooby.MediaType;
import org.jooby.Results;
import org.jooby.funzy.Throwing;
import org.jooby.funzy.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class H2Jetty extends Jooby {

  /** The logging system. */
  private final Logger log = LoggerFactory.getLogger(getClass());

  Throwing.Function<String, String> html = Throwing.<String, String>throwingFunction(path -> {
    return Try.with(() -> getClass().getResourceAsStream(path))
        .apply(in -> {
          byte[] bytes = ByteStreams.toByteArray(in);
          return new String(bytes, StandardCharsets.UTF_8);
        }).get();
  }).memoized();

  {
    http2();
    securePort(8443);

    use("*", (req, rsp) -> {
      log.info("************ {} ************", req.path());
    });

    assets("/assets/**");
    get("/", req -> {
      req.push("/assets/index.js");
      return Results.ok(html.apply("/index.html")).type(MediaType.html);
    });

  }

  public static void main(final String[] args) throws Throwable {
    run(H2Jetty::new, args);
  }
}
