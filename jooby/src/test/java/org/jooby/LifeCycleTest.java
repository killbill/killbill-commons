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

import org.junit.Test;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;

public class LifeCycleTest {

  static class ShouldNotAllowStaticMethod {
    @PostConstruct
    public static void start() {
    }
  }

  static class ShouldNotAllowPrivateMethod {
    @PreDestroy
    private void destroy() {
    }
  }

  static class ShouldNotAllowMethodWithArguments {
    @PostConstruct
    public void start(final int arg) {
    }
  }

  static class ShouldNotAllowMethodWithReturnType {
    @PostConstruct
    public String start() {
      return null;
    }
  }

  static class ShouldNotWrapRuntimeException {
    @PostConstruct
    public void start() {
      throw new RuntimeException("intetional err");
    }
  }

  static class ShouldWrapNoRuntimeException {
    @PostConstruct
    public void start() throws IOException {
      throw new IOException("intetional err");
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void noStaticMethod() {
    LifeCycle.lifeCycleAnnotation(ShouldNotAllowStaticMethod.class, PostConstruct.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldNotAllowPrivateMethod() {
    LifeCycle.lifeCycleAnnotation(ShouldNotAllowPrivateMethod.class, PreDestroy.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldNotAllowMethodWithArguments() {
    LifeCycle.lifeCycleAnnotation(ShouldNotAllowMethodWithArguments.class, PostConstruct.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldNotAllowMethodWithReturnType() {
    LifeCycle.lifeCycleAnnotation(ShouldNotAllowMethodWithReturnType.class, PostConstruct.class);
  }

  @Test(expected = RuntimeException.class)
  public void shouldNotWrapRuntimeExceptin() throws Throwable {
    LifeCycle.lifeCycleAnnotation(ShouldNotWrapRuntimeException.class, PostConstruct.class)
      .get().accept(new ShouldNotWrapRuntimeException());
    ;
  }

  @Test(expected = IOException.class)
  public void shouldWrapNotWrapException() throws Throwable {
    LifeCycle.lifeCycleAnnotation(ShouldWrapNoRuntimeException.class, PostConstruct.class)
      .get().accept(new ShouldWrapNoRuntimeException());
    ;
  }

}
