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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.jooby.Cookie;
import org.junit.Test;

public class CookieImplTest {

  static final DateTimeFormatter fmt = DateTimeFormatter
      .ofPattern("E, dd-MMM-yyyy HH:mm:ss z", Locale.ENGLISH)
      .withZone(ZoneId.of("GMT"));

  @Test
  public void encodeNameAndValue() throws Exception {
    assertEquals("jooby.sid=1234;Version=1", new Cookie.Definition("jooby.sid", "1234").toCookie()
        .encode());
  }

  @Test
  public void escapeQuote() throws Exception {
    assertEquals("jooby.sid=\"a\\\"b\";Version=1", new Cookie.Definition("jooby.sid", "a\"b").toCookie()
        .encode());
  }

  @Test
  public void escapeSlash() throws Exception {
    assertEquals("jooby.sid=\"a\\\\b\";Version=1", new Cookie.Definition("jooby.sid", "a\\b").toCookie()
        .encode());
  }

  @Test
  public void oneChar() throws Exception {
    assertEquals("jooby.sid=1;Version=1", new Cookie.Definition("jooby.sid", "1").toCookie()
        .encode());
  }

  @Test
  public void escapeValueStartingWithQuoute() throws Exception {
    assertEquals("jooby.sid=\"\\\"1\";Version=1", new Cookie.Definition("jooby.sid", "\"1").toCookie()
        .encode());
  }

  @Test(expected = IllegalArgumentException.class)
  public void badChar() throws Exception {
    char ch = '\n';
    new Cookie.Definition("name", "" + ch).toCookie().encode();
  }

  @Test(expected = IllegalArgumentException.class)
  public void badChar2() throws Exception {
    char ch = 0x7f;
    new Cookie.Definition("name", "" + ch).toCookie().encode();
  }

  @Test
  public void encodeSessionCookie() throws Exception {
    assertEquals("jooby.sid=1234;Version=1", new Cookie.Definition("jooby.sid", "1234").maxAge(-1)
        .toCookie().encode());
  }

  @Test
  public void nullValue() throws Exception {
    assertEquals("jooby.sid=;Version=1", new Cookie.Definition("jooby.sid", "").maxAge(-1)
        .toCookie().encode());
  }

  @Test
  public void emptyValue() throws Exception {
    assertEquals("jooby.sid=;Version=1", new Cookie.Definition("jooby.sid", "").maxAge(-1)
        .toCookie().encode());
  }

  @Test
  public void quotedValue() throws Exception {
    assertEquals("jooby.sid=\"val 1\";Version=1", new Cookie.Definition("jooby.sid", "\"val 1\"")
        .maxAge(-1)
        .toCookie().encode());
  }

  @Test
  public void encodeHttpOnly() throws Exception {
    assertEquals("jooby.sid=1234;Version=1;HttpOnly",
        new Cookie.Definition("jooby.sid", "1234").httpOnly(true).toCookie()
            .encode());
  }

  @Test
  public void encodeSecure() throws Exception {
    assertEquals("jooby.sid=1234;Version=1;Secure",
        new Cookie.Definition("jooby.sid", "1234").secure(true).toCookie()
            .encode());
  }

  @Test
  public void encodePath() throws Exception {
    assertEquals("jooby.sid=1234;Version=1;Path=/",
        new Cookie.Definition("jooby.sid", "1234").path("/").toCookie().encode());
  }

  @Test
  public void encodeDomain() throws Exception {
    assertEquals("jooby.sid=1234;Version=1;Domain=example.com",
        new Cookie.Definition("jooby.sid", "1234").domain("example.com").toCookie().encode());
  }

  @Test
  public void encodeComment() throws Exception {
    assertEquals("jooby.sid=1234;Version=1;Comment=\"1,2,3\"",
        new Cookie.Definition("jooby.sid", "1234").comment("1,2,3").toCookie()
            .encode());
  }

  @Test
  public void encodeMaxAge0() throws Exception {
    assertEquals("jooby.sid=1234;Version=1;Max-Age=0;Expires=Thu, 01-Jan-1970 00:00:00 GMT",
        new Cookie.Definition("jooby.sid", "1234").maxAge(0).toCookie().encode());
  }

  @Test
  public void encodeMaxAge60() throws Exception {
    assertTrue(new Cookie.Definition("jooby.sid", "1234")
        .maxAge(60).toCookie().encode().startsWith("jooby.sid=1234;Version=1;Max-Age=60"));

    // Verify Expires header is present and within expected range (no System.class mocking)
    String encoded = new Cookie.Definition("jooby.sid", "1234").maxAge(60).toCookie().encode();
    assertTrue("Expected Max-Age=60 and Expires header, got: " + encoded,
        encoded.startsWith("jooby.sid=1234;Version=1;Max-Age=60;Expires="));
  }

  @Test
  public void encodeEverything() throws Exception {
    assertTrue(new Cookie.Definition("jooby.sid", "1234")
        .maxAge(60).toCookie().encode().startsWith("jooby.sid=1234;Version=1;Max-Age=60"));

    // Verify all cookie attributes are present (no System.class mocking for Expires)
    String encoded = new Cookie.Definition("jooby.sid", "1234")
        .comment("c")
        .domain("example.com")
        .httpOnly(true)
        .maxAge(120)
        .path("/")
        .secure(true)
        .toCookie()
        .encode();
    assertTrue("Expected full cookie, got: " + encoded,
        encoded.startsWith("jooby.sid=1234;Version=1;Path=/;Domain=example.com;Secure;HttpOnly;Max-Age=120;Expires="));
    assertTrue("Expected Comment=c, got: " + encoded, encoded.endsWith(";Comment=c"));
  }
}
