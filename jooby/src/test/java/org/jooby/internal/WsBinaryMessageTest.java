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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import org.jooby.Err;
import org.jooby.Mutant;
import org.jooby.test.MockUnit;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.base.Charsets;

public class WsBinaryMessageTest {

  @Test
  public void toByteArray() {
    byte[] bytes = "bytes".getBytes();
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    assertArrayEquals(bytes, new WsBinaryMessage(buffer).to(byte[].class));
  }

  @Test
  public void toByteBuffer() {
    byte[] bytes = "bytes".getBytes();
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    assertEquals(buffer, new WsBinaryMessage(buffer).to(ByteBuffer.class));
  }

  @Test
  public void toInputStream() throws Exception {
    byte[] bytes = "bytes".getBytes();
    ByteBuffer buffer = ByteBuffer.wrap(bytes);

    new MockUnit()
        .expect(unit -> {
          unit.mockConstructor(ByteArrayInputStream.class,
              new Class[]{byte[].class }, bytes);
        })
        .run(unit -> {
          InputStream result = new WsBinaryMessage(buffer).to(InputStream.class);
          assertNotNull(result);
          assertTrue(Mockito.mockingDetails(result).isMock());
        });
  }

  @Test
  public void toReader() throws Exception {
    byte[] bytes = "bytes".getBytes();
    ByteBuffer buffer = ByteBuffer.wrap(bytes);

    new MockUnit()
        .expect(
            unit -> {
              unit.mockConstructor(ByteArrayInputStream.class,
                  new Class[]{byte[].class }, bytes);

              unit.mockConstructor(InputStreamReader.class, new Class[]{
                  InputStream.class, Charset.class }, null, Charsets.UTF_8);
            })
        .run(unit -> {
          Reader result = new WsBinaryMessage(buffer).to(Reader.class);
          assertNotNull(result);
          assertTrue(Mockito.mockingDetails(result).isMock());
        });
  }

  @Test(expected = Err.class)
  public void toUnsupportedType() throws Exception {
    byte[] bytes = "bytes".getBytes();
    ByteBuffer buffer = ByteBuffer.wrap(bytes);

    new WsBinaryMessage(buffer).to(List.class);
  }

  @Test(expected = Err.class)
  public void booleanValue() throws Exception {
    new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes())).booleanValue();
  }

  @Test(expected = Err.class)
  public void byteValue() throws Exception {
    new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes())).byteValue();
  }

  @Test(expected = Err.class)
  public void shortValue() throws Exception {
    new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes())).shortValue();
  }

  @Test(expected = Err.class)
  public void intValue() throws Exception {
    new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes())).intValue();
  }

  @Test(expected = Err.class)
  public void longValue() throws Exception {
    new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes())).longValue();
  }

  @Test(expected = Err.class)
  public void value() throws Exception {
    new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes())).value();
  }

  @Test(expected = Err.class)
  public void floatValue() throws Exception {
    new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes())).floatValue();
  }

  @Test(expected = Err.class)
  public void doubleValue() throws Exception {
    new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes())).doubleValue();
  }

  @SuppressWarnings("unchecked")
  @Test(expected = Err.class)
  public void enumValue() throws Exception {
    new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes())).toEnum(Enum.class);
  }

  @Test(expected = Err.class)
  public void toList() throws Exception {
    new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes())).toList(String.class);
  }

  @Test(expected = Err.class)
  public void toSet() throws Exception {
    new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes())).toSet(String.class);
  }

  @Test(expected = Err.class)
  public void toSortedSet() throws Exception {
    new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes())).toSortedSet(String.class);
  }

  @Test(expected = Err.class)
  public void toOptional() throws Exception {
    new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes())).toOptional(String.class);
  }

  @Test
  public void isSet() throws Exception {
    assertEquals(true, new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes())).isSet());
  }

  @Test
  public void toMap() throws Exception {
    WsBinaryMessage msg = new WsBinaryMessage(ByteBuffer.wrap("bytes".getBytes()));
    Map<String, Mutant> map = msg.toMap();
    assertEquals(msg, map.get("message"));
  }

}
