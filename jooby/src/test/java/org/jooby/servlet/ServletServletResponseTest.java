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
package org.jooby.servlet;

import org.killbill.commons.utils.io.ByteStreams;
import static org.mockito.Mockito.when;
import org.jooby.funzy.Throwing;
import org.jooby.test.MockUnit;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

public class ServletServletResponseTest {

  @Test
  public void defaults() throws Exception {
    new MockUnit(HttpServletRequest.class, HttpServletResponse.class)
        .run(unit -> {
          new ServletServletResponse(unit.get(HttpServletRequest.class),
              unit.get(HttpServletResponse.class));
        });
  }

  @Test
  public void close() throws Exception {
    new MockUnit(HttpServletRequest.class, HttpServletResponse.class)
        .run(unit -> {
          new ServletServletResponse(unit.get(HttpServletRequest.class),
              unit.get(HttpServletResponse.class)).close();
        });
  }

  @Test
  public void headers() throws Exception {
    new MockUnit(HttpServletRequest.class, HttpServletResponse.class)
        .expect(unit -> {
          HttpServletResponse rsp = unit.get(HttpServletResponse.class);
          when(rsp.getHeaders("h")).thenReturn(Arrays.asList("v"));
        })
        .run(unit -> {
          assertEquals(Arrays.asList("v"),
              new ServletServletResponse(unit.get(HttpServletRequest.class),
                  unit.get(HttpServletResponse.class)).headers("h"));
        });
  }

  @Test
  public void emptyHeaders() throws Exception {
    new MockUnit(HttpServletRequest.class, HttpServletResponse.class)
        .expect(unit -> {
          HttpServletResponse rsp = unit.get(HttpServletResponse.class);
          when(rsp.getHeaders("h")).thenReturn(Collections.emptyList());
        })
        .run(unit -> {
          assertEquals(Collections.emptyList(),
              new ServletServletResponse(unit.get(HttpServletRequest.class),
                  unit.get(HttpServletResponse.class)).headers("h"));
        });
  }

  @Test
  public void noHeaders() throws Exception {
    new MockUnit(HttpServletRequest.class, HttpServletResponse.class)
        .expect(unit -> {
          HttpServletResponse rsp = unit.get(HttpServletResponse.class);
          when(rsp.getHeaders("h")).thenReturn(null);
        })
        .run(unit -> {
          assertEquals(Collections.emptyList(),
              new ServletServletResponse(unit.get(HttpServletRequest.class),
                  unit.get(HttpServletResponse.class)).headers("h"));
        });
  }

  @Test
  public void header() throws Exception {
    new MockUnit(HttpServletRequest.class, HttpServletResponse.class)
        .expect(unit -> {
          HttpServletResponse rsp = unit.get(HttpServletResponse.class);
          when(rsp.getHeader("h")).thenReturn("v");
        })
        .run(unit -> {
          assertEquals(Optional.of("v"),
              new ServletServletResponse(unit.get(HttpServletRequest.class),
                  unit.get(HttpServletResponse.class)).header("h"));
        });
  }

  @Test
  public void emptyHeader() throws Exception {
    new MockUnit(HttpServletRequest.class, HttpServletResponse.class)
        .expect(unit -> {
          HttpServletResponse rsp = unit.get(HttpServletResponse.class);
          when(rsp.getHeader("h")).thenReturn("");
        })
        .run(unit -> {
          assertEquals(Optional.empty(),
              new ServletServletResponse(unit.get(HttpServletRequest.class),
                  unit.get(HttpServletResponse.class)).header("h"));
        });
  }

  @Test
  public void noHeader() throws Exception {
    new MockUnit(HttpServletRequest.class, HttpServletResponse.class)
        .expect(unit -> {
          HttpServletResponse rsp = unit.get(HttpServletResponse.class);
          when(rsp.getHeader("h")).thenReturn(null);
        })
        .run(unit -> {
          assertEquals(Optional.empty(),
              new ServletServletResponse(unit.get(HttpServletRequest.class),
                  unit.get(HttpServletResponse.class)).header("h"));
        });
  }

  @Test
  public void sendBytes() throws Exception {
    byte[] bytes = "bytes".getBytes();
    new MockUnit(HttpServletRequest.class, HttpServletResponse.class, ServletOutputStream.class)
        .expect(unit -> {
          ServletOutputStream output = unit.get(ServletOutputStream.class);
          output.write(bytes);
          output.close();

          HttpServletResponse rsp = unit.get(HttpServletResponse.class);
          rsp.setHeader("Transfer-Encoding", null);
          when(rsp.getOutputStream()).thenReturn(output);
        })
        .run(unit -> {
          new ServletServletResponse(unit.get(HttpServletRequest.class),
              unit.get(HttpServletResponse.class)).send(bytes);
        });
  }

  @Test
  public void sendByteBuffer() throws Exception {
    byte[] bytes = "bytes".getBytes();
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    new MockUnit(HttpServletRequest.class, HttpServletResponse.class, ServletOutputStream.class)
        .expect(unit -> {
          ServletOutputStream output = unit.get(ServletOutputStream.class);

          WritableByteChannel channel = unit.mock(WritableByteChannel.class);
          when(channel.write(buffer)).thenReturn(bytes.length);
          channel.close();

          unit.mockStatic(Channels.class).when(() -> Channels.newChannel(output)).thenReturn(channel);

          HttpServletResponse rsp = unit.get(HttpServletResponse.class);
          when(rsp.getOutputStream()).thenReturn(output);
        })
        .run(unit -> {
          new ServletServletResponse(unit.get(HttpServletRequest.class),
              unit.get(HttpServletResponse.class)).send(buffer);
        });
  }

  @Test
  public void sendFileChannel() throws Exception {
    new MockUnit(HttpServletRequest.class, HttpServletResponse.class, ServletOutputStream.class)
        .expect(unit -> {
          FileChannel channel = unit.mock(FileChannel.class);
          unit.registerMock(FileChannel.class, channel);
        })
        .expect(unit -> {
          FileChannel fchannel = unit.get(FileChannel.class);
          when(fchannel.size()).thenReturn(10L);
          ServletOutputStream output = unit.get(ServletOutputStream.class);

          WritableByteChannel channel = unit.mock(WritableByteChannel.class);

          unit.mockStatic(Channels.class).when(() -> Channels.newChannel(output)).thenReturn(channel);

          when(fchannel.transferTo(0L, 10L, channel)).thenReturn(1L);
          fchannel.close();
          channel.close();

          HttpServletResponse rsp = unit.get(HttpServletResponse.class);
          when(rsp.getOutputStream()).thenReturn(output);
        })
        .run(unit -> {
          new ServletServletResponse(unit.get(HttpServletRequest.class),
              unit.get(HttpServletResponse.class)).send(unit.get(FileChannel.class));
        });
  }

  @Test
  public void sendInputStream() throws Exception {
    new MockUnit(HttpServletRequest.class, HttpServletResponse.class, InputStream.class,
        ServletOutputStream.class)
        .expect(unit -> {
          InputStream in = unit.get(InputStream.class);
          ServletOutputStream output = unit.get(ServletOutputStream.class);

          unit.mockStatic(ByteStreams.class).when(() -> ByteStreams.copy(in, output)).thenReturn(0L);

          output.close();
          in.close();

          HttpServletResponse rsp = unit.get(HttpServletResponse.class);
          when(rsp.getOutputStream()).thenReturn(output);
        })
        .run(unit -> {
          new ServletServletResponse(unit.get(HttpServletRequest.class),
              unit.get(HttpServletResponse.class)).send(unit.get(InputStream.class));
        });
  }

}
