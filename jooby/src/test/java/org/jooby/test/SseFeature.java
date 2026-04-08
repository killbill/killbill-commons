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
package org.jooby.test;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.jooby.Jooby;
import org.jooby.MediaType;
import org.junit.runner.RunWith;

/**
 * Internal use only.
 *
 * @author edgar
 */
@RunWith(JoobySuite.class)
public abstract class SseFeature extends Jooby {

  protected int port;

  public String sse(final String path, final int count) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("Content-Type", MediaType.sse.name())
        .header("last-event-id", Integer.toString(count))
        .GET()
        .build();

    HttpResponse<InputStream> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());

    assertEquals(200, response.statusCode());
    assertEquals("close", response.headers().firstValue("Connection").orElse("").toLowerCase());
    assertEquals("text/event-stream; charset=utf-8",
        response.headers().firstValue("Content-Type").orElse("").toLowerCase());

    try (InputStream body = response.body()) {
      return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
