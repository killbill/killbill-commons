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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jooby.Parser;
import org.jooby.internal.parser.ParserExecutor;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.TypeLiteral;
import com.google.inject.util.Types;
import com.typesafe.config.ConfigFactory;

public class BuiltinParserTest {

  private ParserExecutor executor;

  public enum Color {
    RED,
    GREEN,
    BLUE
  }

  @Before
  public void setup() {
    executor = parser(BuiltinParser.Basic, BuiltinParser.Collection, BuiltinParser.Optional,
        BuiltinParser.Enum, BuiltinParser.Bytes);
  }

  @Test
  public void valuesAreRegisteredInParserOrder() {
    assertArrayEquals(new BuiltinParser[]{
        BuiltinParser.Basic,
        BuiltinParser.Collection,
        BuiltinParser.Optional,
        BuiltinParser.Enum,
        BuiltinParser.Bytes }, BuiltinParser.values());
  }

  @Test
  public void bytesParserName() {
    assertEquals("byte[]", BuiltinParser.Bytes.toString());
  }

  @Test
  public void bytesParserReadsBody() throws Throwable {
    byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

    assertArrayEquals(bytes, executor.convert(TypeLiteral.get(byte[].class),
        new BodyReferenceImpl(bytes.length, StandardCharsets.UTF_8,
            new File("target/BuiltinParserTest/body.tmp"),
            new ByteArrayInputStream(bytes), bytes.length + 1L)));
  }

  @Test
  public void longParserAcceptsHttpDate() throws Throwable {
    LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    assertEquals(dateTime.toInstant(ZoneOffset.UTC).toEpochMilli(),
        (long) executor.convert(TypeLiteral.get(long.class), data(dateTime.format(Headers.fmt))));
  }

  @Test
  public void basicParserAcceptsEmptyStringButRejectsEmptyScalarValues() throws Throwable {
    assertEquals("", executor.convert(TypeLiteral.get(String.class), data("")));

    try {
      executor.convert(TypeLiteral.get(int.class), data(""));
      fail("Expected NoSuchElementException");
    } catch (NoSuchElementException expected) {
      // expected
    }
  }

  @Test
  public void collectionParserBuildsUnmodifiableCollections() throws Throwable {
    List<String> list = executor.convert(TypeLiteral.get(Types.listOf(String.class)),
        data("b", "a", "b"));
    assertEquals(Arrays.asList("b", "a", "b"), list);
    assertUnsupported(() -> list.add("c"));

    Set<String> set = executor.convert(TypeLiteral.get(Types.setOf(String.class)),
        data("b", "a", "b"));
    assertEquals(new LinkedHashSet<>(Arrays.asList("b", "a")), set);
    assertEquals(2, set.size());
    assertUnsupported(() -> set.add("c"));

    SortedSet<String> sorted = executor.convert(
        TypeLiteral.get(Types.newParameterizedType(SortedSet.class, String.class)),
        data("b", "a", "b"));
    assertEquals(new TreeSet<>(Arrays.asList("a", "b")), sorted);
    assertUnsupported(() -> sorted.add("c"));
  }

  @Test
  public void collectionAndOptionalParsersIgnoreRawOrUnsupportedTypes() throws Throwable {
    ParserExecutor collectionOnly = parser(BuiltinParser.Collection);
    assertSame(ParserExecutor.NO_PARSER,
        collectionOnly.convert(TypeLiteral.get(List.class), data("a")));
    assertSame(ParserExecutor.NO_PARSER,
        collectionOnly.convert(TypeLiteral.get(Types.mapOf(String.class, String.class)), data("a")));

    ParserExecutor optionalOnly = parser(BuiltinParser.Optional);
    assertSame(ParserExecutor.NO_PARSER,
        optionalOnly.convert(TypeLiteral.get(Optional.class), data("a")));
  }

  @Test
  public void enumParserIsCaseInsensitive() throws Throwable {
    assertEquals(Color.RED, executor.convert(TypeLiteral.get(Color.class), data("red")));
    assertEquals(Color.GREEN, executor.convert(TypeLiteral.get(Color.class), data("Green")));
    assertEquals(Color.BLUE, executor.convert(TypeLiteral.get(Color.class), data("bLuE")));
  }

  private Object data(final String... values) {
    return new StrParamReferenceImpl("parameter", "p", new ArrayList<>(Arrays.asList(values)));
  }

  private ParserExecutor parser(final Parser... parsers) {
    return new ParserExecutor(null, new LinkedHashSet<>(Arrays.asList(parsers)),
        new StatusCodeProvider(ConfigFactory.empty()));
  }

  private static void assertUnsupported(final Runnable mutation) {
    try {
      mutation.run();
      fail("Expected UnsupportedOperationException");
    } catch (UnsupportedOperationException expected) {
      // expected
    }
  }

}
