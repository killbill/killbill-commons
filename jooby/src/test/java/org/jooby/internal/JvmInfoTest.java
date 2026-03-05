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

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.management.ManagementFactory;

import org.jooby.test.MockUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({JvmInfo.class, ManagementFactory.class })
public class JvmInfoTest {

  @Test
  public void emptyConstructor() {
    new JvmInfo();
  }

  @Test
  public void pid() {
    assertTrue(JvmInfo.pid() > 0);
  }

  @Test
  public void piderr() throws Exception {
    new MockUnit()
    .expect(unit -> {
      unit.mockStatic(ManagementFactory.class);
      expect(ManagementFactory.getRuntimeMXBean()).andThrow(new RuntimeException());
    })
    .run(unit -> {
      assertEquals(-1, JvmInfo.pid());
    });
  }

}
