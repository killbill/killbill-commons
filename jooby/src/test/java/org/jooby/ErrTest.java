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
package org.jooby;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ErrTest {

  @Test
  public void exceptionWithStatus() {
    Err exception = new Err(Status.NOT_FOUND);

    assertEquals(Status.NOT_FOUND.value(), exception.statusCode());
    assertEquals("Not Found(404)", exception.getMessage());
  }

  @Test
  public void exceptionWithIntStatus() {
    Err exception = new Err(404);

    assertEquals(Status.NOT_FOUND.value(), exception.statusCode());
    assertEquals("Not Found(404)", exception.getMessage());
  }

  @Test
  public void exceptionWithStatusAndCause() {
    Exception cause = new IllegalArgumentException();
    Err exception = new Err(Status.NOT_FOUND, cause);

    assertEquals(Status.NOT_FOUND.value(), exception.statusCode());
    assertEquals("Not Found(404)", exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  public void exceptionWithIntStatusAndCause() {
    Exception cause = new IllegalArgumentException();
    Err exception = new Err(404, cause);

    assertEquals(Status.NOT_FOUND.value(), exception.statusCode());
    assertEquals("Not Found(404)", exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  public void exceptionWithStatusAndMessage() {
    Err exception = new Err(Status.NOT_FOUND, "GET/missing");

    assertEquals(Status.NOT_FOUND.value(), exception.statusCode());
    assertEquals("Not Found(404): GET/missing", exception.getMessage());
  }

  @Test
  public void exceptionWithIntStatusAndMessage() {
    Err exception = new Err(404, "GET/missing");

    assertEquals(Status.NOT_FOUND.value(), exception.statusCode());
    assertEquals("Not Found(404): GET/missing", exception.getMessage());
  }

  @Test
  public void exceptionWithStatusCauseAndMessage() {
    Exception cause = new IllegalArgumentException();
    Err exception = new Err(Status.NOT_FOUND, "GET/missing", cause);

    assertEquals(Status.NOT_FOUND.value(), exception.statusCode());
    assertEquals("Not Found(404): GET/missing", exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  public void exceptionWithIntStatusCauseAndMessage() {
    Exception cause = new IllegalArgumentException();
    Err exception = new Err(404, "GET/missing", cause);

    assertEquals(Status.NOT_FOUND.value(), exception.statusCode());
    assertEquals("(404): GET/missing", exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

}
