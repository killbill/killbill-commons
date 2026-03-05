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
package org.jooby.funzy;

import static org.jooby.funzy.Throwing.throwingFunction;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class ThrowingFunctionTest {

  @Test
  public void functionArguments() {
    assertEquals(1, Throwing.throwingFunction(v1 -> {
      assertEquals(1, v1);
      return v1;
    }).apply(1));

    assertEquals("ab", Throwing.<String, String, String>throwingFunction((v1, v2) ->
      v1 + v2
    ).apply("a", "b"));

    assertEquals("abc", Throwing.<String, String, String, String>throwingFunction((v1, v2, v3) ->
      v1 + v2 + v3
    ).apply("a", "b", "c"));

    assertEquals("abcd",
      Throwing.<String, String, String, String, String>throwingFunction((v1, v2, v3, v4) ->
        v1 + v2 + v3 + v4
      ).apply("a", "b", "c", "d"));

    assertEquals("abcde",
      Throwing.<String, String, String, String, String, String>throwingFunction(
        (v1, v2, v3, v4, v5) ->
          v1 + v2 + v3 + v4 + v5
      ).apply("a", "b", "c", "d", "e"));

    assertEquals("abcdef",
      Throwing.<String, String, String, String, String, String, String>throwingFunction(
        (v1, v2, v3, v4, v5, v6) ->
          v1 + v2 + v3 + v4 + v5 + v6
      ).apply("a", "b", "c", "d", "e", "f"));

    assertEquals("abcdefg",
      Throwing.<String, String, String, String, String, String, String, String>throwingFunction(
        (v1, v2, v3, v4, v5, v6, v7) ->
          v1 + v2 + v3 + v4 + v5 + v6 + v7
      ).apply("a", "b", "c", "d", "e", "f", "g"));

    assertEquals("abcdefgh",
      Throwing.<String, String, String, String, String, String, String, String, String>throwingFunction(
        (v1, v2, v3, v4, v5, v6, v7, v8) ->
          v1 + v2 + v3 + v4 + v5 + v6 + v7 + v8
      ).apply("a", "b", "c", "d", "e", "f", "g", "h"));
  }

  @Test(expected = IOException.class)
  public void fn1Throw() {
    Throwing.Function<Integer, String> fn = (v1) -> {
      throw new IOException();
    };
    fn.apply(null);
  }

  @Test(expected = NullPointerException.class)
  public void fn2Throw() {
    Throwing.Function2<Integer, String, String> fn = (v1, v2) -> v1.toString() + v2;
    fn.apply(null, "x");
  }

  @Test(expected = NullPointerException.class)
  public void fn3Throw() {
    Throwing.Function3<Integer, Boolean, String, String> fn = (v1, v2, v3) -> v1.toString() + v2 + v3;
    fn.apply(null, true, "x");
  }

}
