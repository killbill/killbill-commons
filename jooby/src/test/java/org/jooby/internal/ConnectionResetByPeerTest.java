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

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConnectionResetByPeerTest {

  @Test
  public void isConnectionResetByPeer() {
    new ConnectionResetByPeer();
    assertTrue(ConnectionResetByPeer.test(new IOException("connection reset by Peer")));
    assertFalse(ConnectionResetByPeer.test(new IOException()));
    assertFalse(ConnectionResetByPeer.test(new IllegalStateException("connection reset by peer")));
  }
}
