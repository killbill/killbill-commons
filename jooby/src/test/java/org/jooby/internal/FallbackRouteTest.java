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

import java.util.concurrent.atomic.AtomicBoolean;

import org.jooby.MediaType;
import org.jooby.Route;
import org.jooby.Route.Filter;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class FallbackRouteTest {

  @Test
  public void props() throws Throwable {
    AtomicBoolean handled = new AtomicBoolean(false);
    Filter filter = (req, rsp, chain) -> {
      handled.set(true);
    };
    FallbackRoute route = new FallbackRoute("foo", "GET", "/x", ImmutableList.of(MediaType.json),
        filter);

    assertEquals(true, route.apply(null));
    assertEquals(0, route.attributes().size());
    assertEquals(0, route.vars().size());
    assertEquals(MediaType.ALL, route.consumes());
    assertEquals(false, route.glob());
    assertEquals("foo", route.name());
    assertEquals("/x", route.path());
    assertEquals("/x", route.pattern());
    assertEquals(ImmutableList.of(MediaType.json), route.produces());
    assertEquals("/x", route.reverse(ImmutableMap.of()));
    assertEquals("/x", route.reverse("a", "b"));
    assertEquals(Route.Source.BUILTIN, route.source());
    route.handle(null, null, null);
    assertEquals(true, handled.get());
  }
}
