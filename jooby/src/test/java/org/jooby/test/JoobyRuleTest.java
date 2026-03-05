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

import org.jooby.Jooby;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({JoobyRule.class, Jooby.class })
public class JoobyRuleTest {

  @Test
  public void before() throws Exception {
    new MockUnit(Jooby.class)
        .expect(unit -> {
          Jooby app = unit.get(Jooby.class);
          app.start("server.join=false");
        })
        .run(unit -> {
          new JoobyRule(unit.get(Jooby.class)).before();
        });
  }

  @Test
  public void after() throws Exception {
    new MockUnit(Jooby.class)
        .expect(unit -> {
          Jooby app = unit.get(Jooby.class);
          app.stop();
        })
        .run(unit -> {
          new JoobyRule(unit.get(Jooby.class)).after();
        });
  }
}
