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
package org.jooby.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.NoSuchElementException;

import org.junit.Test;

public class SSIHandlerTest {

  @Test
  public void missingResourceIncludesPathInMessage() throws Exception {
    SSIHandler handler = new SSIHandler();

    Method fileMethod = SSIHandler.class.getDeclaredMethod("file", String.class);
    fileMethod.setAccessible(true);

    try {
      fileMethod.invoke(handler, "/nonexistent/resource.html");
      fail("Expected NoSuchElementException");
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      assertTrue("Expected NoSuchElementException but got " + cause.getClass(),
          cause instanceof NoSuchElementException);
      String message = cause.getMessage();
      assertTrue("Exception message should contain the resource path, got: " + message,
          message.contains("/nonexistent/resource.html"));
    }
  }

  @Test
  public void existingResourceReturnsContent() throws Exception {
    SSIHandler handler = new SSIHandler();

    Method fileMethod = SSIHandler.class.getDeclaredMethod("file", String.class);
    fileMethod.setAccessible(true);

    // Use a resource that definitely exists on the classpath
    String result = (String) fileMethod.invoke(handler, "/org/jooby/mime.properties");
    assertTrue("Should return non-empty content", result != null && !result.isEmpty());
  }
}
