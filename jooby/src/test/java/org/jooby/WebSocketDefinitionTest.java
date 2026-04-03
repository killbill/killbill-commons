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
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class WebSocketDefinitionTest {

  @Test
  public void toStr() {
    WebSocket.Definition def = new WebSocket.Definition("/pattern", (req, ws) -> {
    });

    assertEquals("WS /pattern\n" +
        "  consume: text/plain\n" +
        "  produces: text/plain\n", def.toString());
  }

  @Test
  public void matches() {
    WebSocket.Definition def = new WebSocket.Definition("/pattern", (req, ws) -> {
    });

    assertEquals(true, def.matches("/pattern").isPresent());
    assertEquals(false, def.matches("/patter").isPresent());
  }

  @Test
  public void consumes() {
    assertEquals(MediaType.json, new WebSocket.Definition("/pattern", (req, ws) -> {
    }).consumes("json").consumes());
  }

  @Test(expected = NullPointerException.class)
  public void consumesNull() {
    new WebSocket.Definition("/pattern", (req, ws) -> {
    }).consumes((MediaType) null);

  }

  @Test
  public void produces() {
    assertEquals(MediaType.json, new WebSocket.Definition("/pattern", (req, ws) -> {
    }).produces("json").produces());
  }

  @Test(expected = NullPointerException.class)
  public void producesNull() {
    new WebSocket.Definition("/pattern", (req, ws) -> {
    }).produces((MediaType) null);
  }

  @Test
  public void identity() {
    assertEquals(
        new WebSocket.Definition("/pattern", (req, ws) -> {
        }),
        new WebSocket.Definition("/pattern", (req, ws) -> {
        }));

    assertEquals(
        new WebSocket.Definition("/pattern", (req, ws) -> {
        }).hashCode(),
        new WebSocket.Definition("/pattern", (req, ws) -> {
        }).hashCode());

    assertNotEquals(
        new WebSocket.Definition("/path", (req, ws) -> {
        }),
        new WebSocket.Definition("/patternx", (req, ws) -> {
        }));

    assertNotEquals(
        new WebSocket.Definition("/patternx", (req, ws) -> {
        }),
        new Object());
  }

}
