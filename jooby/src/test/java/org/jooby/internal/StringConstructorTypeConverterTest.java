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

import java.util.Locale;

import org.jooby.internal.parser.LocaleParser;
import org.jooby.internal.parser.StringConstructorParser;
import org.jooby.test.MockUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.inject.TypeLiteral;

@RunWith(PowerMockRunner.class)
@PrepareForTest({StringConstructTypeConverter.class, LocaleParser.class,
    StringConstructorParser.class })
public class StringConstructorTypeConverterTest {

  @Test
  public void toLocale() throws Exception {
    TypeLiteral<Locale> type = TypeLiteral.get(Locale.class);
    new MockUnit()
        .run(unit -> {
          assertEquals(LocaleUtils.parseOne("x"),
              new StringConstructTypeConverter<Object>().convert("x", type));
        });
  }

  @Test(expected = IllegalStateException.class)
  public void runtimeError() throws Exception {
    TypeLiteral<Object> type = TypeLiteral.get(Object.class);
    new MockUnit()
        .expect(unit -> {
          unit.mockStatic(StringConstructorParser.class);
          expect(StringConstructorParser.parse(type, "y")).andThrow(
              new IllegalArgumentException("intentional err"));
        })
        .run(unit -> {
          new StringConstructTypeConverter<Object>().convert("y", type);
        });
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked" })
  public void shouldNotMatchMissingStringConstructor() throws Exception {
    TypeLiteral type = TypeLiteral.get(StringConstructorTypeConverterTest.class);
    new MockUnit()
        .run(unit -> {
          assertEquals(false, new StringConstructTypeConverter<Object>().matches(type));
        });
  }

  @Test
  public void shouldMatchStringConstructor() throws Exception {
    TypeLiteral<Locale> type = TypeLiteral.get(Locale.class);
    assertEquals(true, new StringConstructTypeConverter<Locale>().matches(type));
  }

  @Test
  public void describe() throws Exception {
    assertEquals("TypeConverter init(java.lang.String)",
        new StringConstructTypeConverter<Package>().toString());
  }
}
