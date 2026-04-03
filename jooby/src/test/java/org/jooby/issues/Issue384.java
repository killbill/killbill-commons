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
package org.jooby.issues;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jooby.Route;
import org.jooby.Route.Mapper;
import org.junit.Test;

public class Issue384 {

  static class M implements Route.Mapper<Integer> {

    @Override
    public Object map(final Integer value) throws Throwable {
      return value;
    }

  }

  @Test
  public void defaultRouteMapperName() {
    Route.Mapper<Integer> intMapper = (final Integer v) -> v * 2;
    assertTrue(intMapper.name().startsWith("issue384"));

    assertEquals("m", new M().name());

    assertTrue(new Route.Mapper<String>() {
      @Override
      public Object map(final String value) throws Throwable {
        return value;
      };
    }.name().startsWith("issue384"));
  }

  @Test
  public void routeFactory() {
    Mapper<Integer> intMapper = Route.Mapper.create("x", (final Integer v) -> v * 2);
    assertEquals("x", intMapper.name());
    assertEquals("x", intMapper.toString());
  }

  @Test
  public void chain() throws Throwable {
    Mapper<Integer> intMapper = Route.Mapper.create("int", (final Integer v) -> v * 2);
    Mapper<String> strMapper = Route.Mapper.create("str", v -> "{" + v + "}");
    assertEquals("int>str", Route.Mapper.chain(intMapper, strMapper).name());
    assertEquals("str>int", Route.Mapper.chain(strMapper, intMapper).name());
    assertEquals(8, Route.Mapper.chain(intMapper, intMapper).map(2));
  }
}
