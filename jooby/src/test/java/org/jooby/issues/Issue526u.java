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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.function.Consumer;

import org.jooby.internal.RouteMatcher;
import org.jooby.internal.RoutePattern;
import org.junit.Test;

public class Issue526u {

  class RoutePathAssert {

    RoutePattern path;

    public RoutePathAssert(final String method, final String pattern) {
      path = new RoutePattern(method, pattern);
    }

    public RoutePathAssert matches(final String path) {
      return matches(path, (vars) -> {
      });
    }

    public RoutePathAssert matches(final String path, final Consumer<Map<Object, String>> vars) {
      String message = this.path + " != " + path;
      RouteMatcher matcher = this.path.matcher(path);
      boolean matches = matcher.matches();
      if (!matches) {
        System.err.println(message);
      }
      assertTrue(message, matches);
      vars.accept(matcher.vars());
      return this;
    }

    public RoutePathAssert butNot(final String path) {
      String message = this.path + " == " + path;
      RouteMatcher matcher = this.path.matcher(path);
      boolean matches = matcher.matches();
      if (matches) {
        System.err.println(message);
      }
      assertFalse(message, matches);
      return this;
    }
  }

  @Test
  public void shouldAcceptAdvancedRegexPathExpression() {
    new RoutePathAssert("GET", "/V{var:\\d{4,7}}")
        .matches("GET/V1234", (vars) -> {
          assertEquals("1234", vars.get("var"));
        })
        .matches("GET/V1234567", (vars) -> {
          assertEquals("1234567", vars.get("var"));
        })
        .butNot("GET/V123")
        .butNot("GET/V12345678");
  }

  @Test
  public void shouldAcceptSpecialChars() {
    new RoutePathAssert("GET", "/:var")
        .matches("GET/x%252Fy%252Fz", (vars) -> {
          assertEquals("x%252Fy%252Fz", vars.get("var"));
        })
        .butNot("GET/user/123/x")
        .butNot("GET/user/123x")
        .butNot("GET/user/xqi");
  }
}
