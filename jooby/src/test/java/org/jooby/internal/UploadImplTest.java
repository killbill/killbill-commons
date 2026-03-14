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

import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jooby.MediaType;
import org.jooby.Mutant;
import org.jooby.internal.parser.ParserExecutor;
import org.jooby.spi.NativeUpload;
import org.jooby.test.MockUnit;
import org.junit.Test;

import com.google.inject.Injector;

public class UploadImplTest {

  @Test
  public void close() throws Exception {
    new MockUnit(Injector.class, NativeUpload.class)
        .expect(unit -> {
          unit.get(NativeUpload.class).close();
        })
        .run(unit -> {
          new UploadImpl(unit.get(Injector.class), unit.get(NativeUpload.class)).close();
        });
  }

  @Test
  public void name() throws Exception {
    new MockUnit(Injector.class, NativeUpload.class)
        .expect(unit -> {
          when(unit.get(NativeUpload.class).name()).thenReturn("x");
        })
        .run(unit -> {
          assertEquals("x",
              new UploadImpl(unit.get(Injector.class), unit.get(NativeUpload.class)).name());
        });
  }

  @Test
  public void describe() throws Exception {
    new MockUnit(Injector.class, NativeUpload.class)
        .expect(unit -> {
          when(unit.get(NativeUpload.class).name()).thenReturn("x");
        })
        .run(unit -> {
          assertEquals("x",
              new UploadImpl(unit.get(Injector.class), unit.get(NativeUpload.class)).toString());
        });
  }

  @Test
  public void file() throws Exception {
    File f = new File("x");
    new MockUnit(Injector.class, NativeUpload.class)
        .expect(unit -> {
          when(unit.get(NativeUpload.class).file()).thenReturn(f);
        })
        .run(unit -> {
          assertEquals(f,
              new UploadImpl(unit.get(Injector.class), unit.get(NativeUpload.class)).file());
        });
  }

  @Test
  public void type() throws Exception {
    new MockUnit(Injector.class, NativeUpload.class, ParserExecutor.class)
        .expect(unit -> {
          when(unit.get(Injector.class).getInstance(ParserExecutor.class)).thenReturn(
              unit.get(ParserExecutor.class));
        })
        .expect(
            unit -> {
              NativeUpload upload = unit.get(NativeUpload.class);

              List<String> headers = Arrays.asList("application/json");
              when(upload.headers("Content-Type")).thenReturn(headers);

              StrParamReferenceImpl pref = unit.mockConstructor(StrParamReferenceImpl.class,
                  new Class[]{
                      String.class, String.class, List.class },
                  "header", "Content-Type", headers);

              Mutant mutant = unit.mockConstructor(MutantImpl.class,
                  new Class[]{ParserExecutor.class, Object.class },
                  unit.get(ParserExecutor.class), pref);

              when(mutant.toOptional(MediaType.class))
                  .thenReturn(Optional.ofNullable(MediaType.json));
            })
        .run(unit -> {
          assertEquals(MediaType.json,
              new UploadImpl(unit.get(Injector.class), unit.get(NativeUpload.class)).type());
        });
  }

  @Test
  public void deftype() throws Exception {
    new MockUnit(Injector.class, NativeUpload.class, ParserExecutor.class)
        .expect(unit -> {
          when(unit.get(Injector.class).getInstance(ParserExecutor.class)).thenReturn(
              unit.get(ParserExecutor.class));
        })
        .expect(unit -> {
          when(unit.get(NativeUpload.class).name()).thenReturn("x");
        })
        .expect(unit -> {
          NativeUpload upload = unit.get(NativeUpload.class);

          List<String> headers = Arrays.asList();
          when(upload.headers("Content-Type")).thenReturn(headers);

          StrParamReferenceImpl pref = unit.mockConstructor(StrParamReferenceImpl.class,
              new Class[]{
                  String.class, String.class, List.class },
               "header", "Content-Type", headers);

          Mutant mutant = unit.mockConstructor(MutantImpl.class,
              new Class[]{ParserExecutor.class, Object.class },
              unit.get(ParserExecutor.class), pref);

          when(mutant.toOptional(MediaType.class))
              .thenReturn(Optional.ofNullable(null));
        })
        .run(unit -> {
          assertEquals(MediaType.octetstream,
              new UploadImpl(unit.get(Injector.class), unit.get(NativeUpload.class)).type());
        });
  }

  @Test
  public void typeFromName() throws Exception {
    new MockUnit(Injector.class, NativeUpload.class, ParserExecutor.class)
        .expect(unit -> {
          when(unit.get(Injector.class).getInstance(ParserExecutor.class)).thenReturn(
              unit.get(ParserExecutor.class));
        })
        .expect(unit -> {
          when(unit.get(NativeUpload.class).name()).thenReturn("x.js");
        })
        .expect(unit -> {
          NativeUpload upload = unit.get(NativeUpload.class);

          List<String> headers = Arrays.asList();
          when(upload.headers("Content-Type")).thenReturn(headers);

          StrParamReferenceImpl pref = unit.mockConstructor(StrParamReferenceImpl.class,
              new Class[]{
                  String.class, String.class, List.class },
              "header", "Content-Type", headers);

          Mutant mutant = unit.mockConstructor(MutantImpl.class,
              new Class[]{ParserExecutor.class, Object.class },
              unit.get(ParserExecutor.class), pref);

          when(mutant.toOptional(MediaType.class))
              .thenReturn(Optional.ofNullable(null));
        })
        .run(unit -> {
          assertEquals(MediaType.js,
              new UploadImpl(unit.get(Injector.class), unit.get(NativeUpload.class)).type());
        });
  }

}
