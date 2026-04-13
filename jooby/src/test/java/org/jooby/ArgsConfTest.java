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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ArgsConfTest {

  @Test
  public void keypair() {
    Config args = Jooby.args(new String[]{"p.foo=bar", "p.bar=foo" });
    assertEquals("bar", args.getConfig("p").getString("foo"));
    assertEquals("foo", args.getConfig("p").getString("bar"));
  }

  @Test
  public void env() {
    Config args = Jooby.args(new String[]{"foo" });
    assertEquals("foo", args.getConfig("application").getString("env"));
  }

  @Test
  public void defnamespace() {
    Config args = Jooby.args(new String[]{"port=8080" });
    assertEquals(8080, args.getConfig("application").getInt("port"));
    assertEquals(8080, args.getInt("port"));
  }

  @Test
  public void noargs() {
    assertEquals(ConfigFactory.empty(), Jooby.args(null));
    assertEquals(ConfigFactory.empty(), Jooby.args(new String[0]));
  }

}
