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

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;

import org.jooby.Cookie.Signature;
import org.jooby.test.MockUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@PowerMockIgnore("javax.crypto.*")
@RunWith(PowerMockRunner.class)
public class CookieSignatureTest {

  @Test
  public void sillyJacoco() throws Exception {
    new Cookie.Signature();
  }

  @Test
  public void sign() throws Exception {
    assertEquals("qAlLNkSRVE4aZb+tz6avvkVIEmmR30BH8cpr3x9ZdFA|jooby",
        Signature.sign("jooby", "124Qwerty"));
  }

  @Test(expected = IllegalArgumentException.class)
  @PrepareForTest({Cookie.class, Cookie.Signature.class, Mac.class })
  public void noSuchAlgorithmException() throws Exception {
    new MockUnit()
    .expect(unit -> {
      unit.mockStatic(Mac.class);
      expect(Mac.getInstance("HmacSHA256")).andThrow(new NoSuchAlgorithmException("HmacSHA256"));
    })
    .run(unit -> {
      Signature.sign("jooby", "a11");
    });
  }

  @Test
  public void unsign() throws Exception {
    assertEquals("jooby",
        Signature.unsign("qAlLNkSRVE4aZb+tz6avvkVIEmmR30BH8cpr3x9ZdFA|jooby", "124Qwerty"));
  }

  @Test
  public void valid() throws Exception {
    assertEquals(true,
        Signature.valid("qAlLNkSRVE4aZb+tz6avvkVIEmmR30BH8cpr3x9ZdFA|jooby", "124Qwerty"));
  }

  @Test
  public void invalid() throws Exception {
    assertEquals(false,
        Signature.valid("QAlLNkSRVE4aZb+tz6avvkVIEmmR30BH8cpr3x9ZdFA|jooby", "124Qwerty"));

    assertEquals(false,
        Signature.valid("qAlLNkSRVE4aZb+tz6avvkVIEmmR30BH8cpr3x9ZdFA|joobi", "124Qwerty"));

    assertEquals(false,
        Signature.valid("#qAlLNkSRVE4aZb+tz6avvkVIEmmR30BH8cpr3x9ZdFA#joobi", "124Qwerty"));
  }

}
