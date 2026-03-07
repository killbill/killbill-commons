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
package org.jooby.internal.mvc;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import org.jooby.Env;
import org.jooby.internal.RouteMetadata;
import org.jooby.test.MockUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(RequestParam.class  )
public class RequestParamNameProviderTest {

  public void dummy(final String dummyparam) {

  }

  @Test
  public void asmname() throws Exception {
    Method m = RequestParamNameProviderTest.class.getDeclaredMethod("dummy", String.class);
    Parameter param = m.getParameters()[0];
    new MockUnit(Env.class)
        .expect(unit -> {
          Env env = unit.get(Env.class);
          expect(env.name()).andReturn("dev");
        })
        .expect(unit -> {
          unit.mockStatic(RequestParam.class);
          expect(RequestParam.nameFor(param)).andReturn(null);
        })
        .run(unit -> {
          assertEquals("dummyparam",
              new RequestParamNameProviderImpl(new RouteMetadata(unit.get(Env.class))).name(param));
        });

  }
}
