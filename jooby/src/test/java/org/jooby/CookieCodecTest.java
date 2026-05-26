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

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class CookieCodecTest {

  @Test
  public void encode() {
    assertEquals("success=OK", Cookie.URL_ENCODER.apply(Map.of("success", "OK")));
    assertEquals("success=semi%3Bcolon",
        Cookie.URL_ENCODER.apply(Map.of("success", "semi;colon")));
    assertEquals("success=eq%3Duals",
        Cookie.URL_ENCODER.apply(Map.of("success", "eq=uals")));

    Map<String, String> map = new LinkedHashMap<>();
    map.put("success", "OK");
    map.put("error", "404");
    assertEquals("success=OK&error=404", Cookie.URL_ENCODER.apply(map));
  }

  @Test
  public void decode() {
    assertEquals(Map.of("success", "OK"), Cookie.URL_DECODER.apply("success=OK"));
    assertEquals(Map.of("success", "OK", "foo", "bar"),
        Cookie.URL_DECODER.apply("success=OK&foo=bar"));
    assertEquals(Map.of("semicolon", "semi;colon"),
        Cookie.URL_DECODER.apply("semicolon=semi%3Bcolon"));
  }
}
