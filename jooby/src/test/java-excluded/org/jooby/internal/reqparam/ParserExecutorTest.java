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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jooby.Parser;
import org.jooby.internal.StatusCodeProvider;
import org.jooby.internal.parser.ParserExecutor;
import org.jooby.test.MockUnit;
import org.junit.Test;

import com.google.common.collect.Sets;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import com.typesafe.config.ConfigFactory;

public class ParserExecutorTest {

  @Test
  public void params() throws Exception {
    new MockUnit(Injector.class)
        .run(unit -> {
          Set<Parser> parsers = Sets.newHashSet((Parser) (type, ctx) -> ctx.params(up -> "p"));
          Object converted = new ParserExecutor(unit.get(Injector.class), parsers,
              new StatusCodeProvider(ConfigFactory.empty()))
                  .convert(TypeLiteral.get(Map.class), new HashMap<>());
          assertEquals("p", converted);
        });
  }

}
