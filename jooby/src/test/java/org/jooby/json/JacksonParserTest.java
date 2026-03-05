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
package org.jooby.json;

import static org.easymock.EasyMock.expect;

import org.jooby.MediaType;
import org.jooby.Parser;
import org.jooby.Parser.Context;
import org.jooby.test.MockUnit;
import org.junit.Test;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.TypeLiteral;

public class JacksonParserTest {

  @Test
  public void parseAny() throws Exception {
    Object value = new Object();
    new MockUnit(ObjectMapper.class, Parser.Context.class, MediaType.class)
        .expect(unit -> {
          MediaType type = unit.get(MediaType.class);
          expect(type.isAny()).andReturn(true);

          Context ctx = unit.get(Parser.Context.class);
          expect(ctx.type()).andReturn(type);
          expect(ctx.next()).andReturn(value);
        })
        .run(unit -> {
          new JacksonParser(unit.get(ObjectMapper.class), MediaType.json)
              .parse(TypeLiteral.get(JacksonParserTest.class), unit.get(Parser.Context.class));
        });
  }

  @Test
  public void parseSkip() throws Exception {
    Object value = new Object();
    new MockUnit(ObjectMapper.class, Parser.Context.class, MediaType.class, TypeLiteral.class)
        .expect(unit -> {
          MediaType type = unit.get(MediaType.class);
          expect(type.isAny()).andReturn(false);

          Context ctx = unit.get(Parser.Context.class);
          expect(ctx.type()).andReturn(type);
          expect(ctx.next()).andReturn(value);

          JavaType javaType = unit.mock(JavaType.class);

          ObjectMapper mapper = unit.get(ObjectMapper.class);
          expect(mapper.constructType(null)).andReturn(javaType);
        })
        .run(unit -> {
          new JacksonParser(unit.get(ObjectMapper.class), MediaType.json)
              .parse(unit.get(TypeLiteral.class), unit.get(Parser.Context.class));
        });
  }
}
