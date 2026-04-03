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

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.Date;

import org.junit.Test;

public class HeadersTest {

  @Test
  public void sillyJacoco() {
    new Headers();
  }

  @Test
  public void encodeString() {
    assertEquals("x1", Headers.encode("x1"));
  }

  @Test
  public void encodeNumber() {
    assertEquals("12", Headers.encode(12));
  }

  @Test
  public void date() {
    assertEquals("Fri, 10 Apr 2015 23:31:25 GMT", Headers.encode(new Date(1428708685066L)));
  }

  @Test
  public void calendar() {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(1428708685066L);
    assertEquals("Fri, 10 Apr 2015 23:31:25 GMT", Headers.encode(calendar));
  }

}
