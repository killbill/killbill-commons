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

import org.jooby.Jooby.MvcClass;
import org.jooby.Route.Definition;
import org.junit.Test;

public class MvcClassTest {

  @Test
  public void rendererAttr() throws Exception {
    MvcClass mvcClass = new Jooby.MvcClass(MvcClassTest.class, "/", null);
    mvcClass.renderer("text");
    assertEquals("text", mvcClass.renderer());
    Definition route = new Route.Definition("GET", "/", (req, rsp, chain) -> {
    });
    mvcClass.apply(route);
    assertEquals("text", route.renderer());
  }
}
