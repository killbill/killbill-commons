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
package org.jooby.issues;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jooby.Err;
import org.jooby.Jooby;
import org.jooby.Status;
import org.junit.Test;

public class Issue576 {

  @Test
  public void shouldThrowBootstrapException() {
    IllegalStateException ies = new IllegalStateException("boot err");
    try {
      new Jooby() {
        {
          throwBootstrapException();

          onStart(() -> {
            throw ies;
          });
        }
      }.start();
      fail();
    } catch (Err err) {
      assertEquals(Status.SERVICE_UNAVAILABLE.value(), err.statusCode());
      assertEquals(ies, err.getCause());
    }
  }

}
