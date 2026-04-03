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
package org.jooby.internal.reqparam;

import static org.junit.Assert.assertEquals;

import org.jooby.internal.parser.StaticMethodParser;
import org.junit.Test;

import com.google.inject.TypeLiteral;

public class StaticMethodParserTest {

  public static class Value {

    private String val;

    private Value(final String val) {
      this.val = val;
    }

    public static Value valueOf(final String val) {
      return new Value(val);
    }

    @Override
    public String toString() {
      return val;
    }

  }

  public static class ValueOfNoStatic {

    public ValueOfNoStatic valueOf() {
      return new ValueOfNoStatic();
    }

  }

  public static class ValueOfNoPublic {

    @SuppressWarnings("unused")
    private static ValueOfNoStatic valueOf() {
      return new ValueOfNoStatic();
    }

  }

  public static class ValueOfNoPublicNoStatic {

    ValueOfNoStatic valueOf() {
      return new ValueOfNoStatic();
    }

  }

  @Test
  public void defaults() throws Exception {
    new StaticMethodParser("valueOf");
  }

  @Test(expected = NullPointerException.class)
  public void nullArg() throws Exception {
    new StaticMethodParser(null);
  }

  @Test
  public void matches() throws Exception {
    assertEquals(true, new StaticMethodParser("valueOf").matches(TypeLiteral.get(Value.class)));

    assertEquals(false,
        new StaticMethodParser("valueOf").matches(TypeLiteral.get(ValueOfNoStatic.class)));

    assertEquals(false,
        new StaticMethodParser("valueOf").matches(TypeLiteral.get(ValueOfNoPublic.class)));

    assertEquals(false,
        new StaticMethodParser("valueOf").matches(TypeLiteral.get(ValueOfNoPublicNoStatic.class)));
  }

}
