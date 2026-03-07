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

import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

import org.jooby.internal.parser.LocaleParser;
import org.jooby.internal.parser.StaticMethodParser;
import org.jooby.test.MockUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.inject.TypeLiteral;

@RunWith(PowerMockRunner.class)
@PrepareForTest({StaticMethodTypeConverter.class, LocaleParser.class,
    StaticMethodParser.class })
public class StaticMethodTypeConverterTest {

  @Test
  public void toAnythingElse() throws Exception {
    TypeLiteral<Object> type = TypeLiteral.get(Object.class);
    new MockUnit()
        .expect(unit -> {
          StaticMethodParser converter = unit
              .mockConstructor(StaticMethodParser.class, new Class[]{String.class },
                  "valueOf");
          expect(converter.parse(eq(type), eq("y"))).andReturn("x");
        })
        .run(unit -> {
          assertEquals("x", new StaticMethodTypeConverter<Object>("valueOf").convert("y", type));
        });
  }

  @Test(expected = IllegalStateException.class)
  public void runtimeError() throws Exception {
    TypeLiteral<Object> type = TypeLiteral.get(Object.class);
    new MockUnit()
        .expect(unit -> {
          StaticMethodParser converter = unit
              .mockConstructor(StaticMethodParser.class, new Class[]{String.class },
                  "valueOf");
          expect(converter.parse(eq(type), eq("y")))
              .andThrow(new IllegalArgumentException("intentional err"));
        })
        .run(unit -> {
          new StaticMethodTypeConverter<Object>("valueOf").convert("y", type);
        });
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked" })
  public void shouldNotMatchEnums() throws Exception {
    TypeLiteral type = TypeLiteral.get(Enum.class);
    new MockUnit()
        .run(unit -> {
          assertEquals(false, new StaticMethodTypeConverter<Object>("valueOf").matches(type));
        });
  }

  @Test
  public void shouldStaticMethod() throws Exception {
    TypeLiteral<Package> type = TypeLiteral.get(Package.class);
    assertEquals(true, new StaticMethodTypeConverter<Package>("getPackage").matches(type));
  }

  @Test
  public void describe() throws Exception {
    assertEquals("forName(String)", new StaticMethodTypeConverter<Package>("forName").toString());
  }
}
