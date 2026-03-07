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
package org.jooby.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.runner.Runner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

/**
 * JUnit suite for Jooby. Internal use only.
 *
 * @author edgar
 */
public class JoobySuite extends Suite {

  private List<Runner> runners;

  static {
    System.setProperty("io.netty.leakDetectionLevel", "advanced");
  }

  public JoobySuite(final Class<?> klass) throws InitializationError {
    super(klass, Collections.emptyList());

    runners = runners(klass);
  }

  @SuppressWarnings("rawtypes")
  private List<Runner> runners(final Class<?> klass) throws InitializationError {
    List<Runner> runners = new ArrayList<>();
    Predicate<Class> filter = Predicates.alwaysTrue();
    OnServer onserver = klass.getAnnotation(OnServer.class);
    if (onserver != null) {
      List<Class<?>> server = Arrays.asList(onserver.value());
      filter = server::contains;
    }
    String[] servers = {"org.jooby.undertow.Undertow", "org.jooby.jetty.Jetty",
        "org.jooby.netty.Netty" };
    for (String server : servers) {
      try {
        Class<?> serverClass = getClass().getClassLoader().loadClass(server);
        if (filter.apply(serverClass)) {
          runners.add(new JoobyRunner(getTestClass().getJavaClass(), serverClass));
        }
      } catch (ClassNotFoundException ex) {
        // do nothing
      }
    }
    return runners;
  }

  @Override
  protected List<Runner> getChildren() {
    return runners;
  }
}
