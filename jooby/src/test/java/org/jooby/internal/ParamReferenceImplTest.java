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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.jooby.test.MockUnit;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class ParamReferenceImplTest {

  @Test
  public void defaults() throws Exception {
    new MockUnit()
        .run(unit -> {
          new StrParamReferenceImpl("parameter", "name", Collections.emptyList());
        });
  }

  @Test
  public void first() throws Exception {
    new MockUnit()
        .run(unit -> {
          assertEquals("first",
              new StrParamReferenceImpl("parameter", "name", ImmutableList.of("first")).first());
        });
  }

  @Test
  public void last() throws Exception {
    new MockUnit()
        .run(unit -> {
          assertEquals("last",
              new StrParamReferenceImpl("parameter", "name", ImmutableList.of("last")).last());
        });
  }

  @Test
  public void get() throws Exception {
    new MockUnit()
        .run(unit -> {
          assertEquals("0",
              new StrParamReferenceImpl("parameter", "name", ImmutableList.of("0")).get(0));
          assertEquals("1",
              new StrParamReferenceImpl("parameter", "name", ImmutableList.of("0", "1")).get(1));
        });
  }

  @Test(expected = NoSuchElementException.class)
  public void missing() throws Exception {
    new MockUnit()
        .run(unit -> {
          new StrParamReferenceImpl("parameter", "name", ImmutableList.of("0")).get(1);
        });
  }

  @Test(expected = NoSuchElementException.class)
  public void missingLowIndex() throws Exception {
    new MockUnit()
        .run(unit -> {
          new StrParamReferenceImpl("parameter", "name", ImmutableList.of("0")).get(-1);
        });
  }

  @Test
  public void size() throws Exception {
    new MockUnit()
        .run(unit -> {
          assertEquals(1,
              new StrParamReferenceImpl("parameter", "name", ImmutableList.of("0")).size());
          assertEquals(2,
              new StrParamReferenceImpl("parameter", "name", ImmutableList.of("0", "1")).size());
        });
  }

  @SuppressWarnings({"rawtypes", "unchecked" })
  @Test
  public void iterator() throws Exception {
    new MockUnit(List.class, Iterator.class)
        .expect(unit -> {
          List list = unit.get(List.class);
          expect(list.iterator()).andReturn(unit.get(Iterator.class));
        })
        .run(unit -> {
          assertEquals(unit.get(Iterator.class),
              new StrParamReferenceImpl("parameter", "name", unit.get(List.class)).iterator());
        });
  }

}
