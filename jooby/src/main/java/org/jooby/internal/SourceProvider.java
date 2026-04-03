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

import java.util.Optional;
import java.util.function.Predicate;

public class SourceProvider {
  private static final Predicate<String> JOOBY_PKG = pkg("org.jooby");
  private static final Predicate<String> JAVALANG_PKG = pkg("javaslang");
  private static final Predicate<String> GOOGLE_PKG = pkg("com.google");
  private static final Predicate<String> SUN_PKG = pkg("sun.").or(pkg("com.sun"));
  private static final Predicate<String> JAVA_PKG = pkg("java.");
  private static final Predicate<String> SKIP = JOOBY_PKG.or(GOOGLE_PKG).or(JAVALANG_PKG)
      .or(SUN_PKG).or(JAVA_PKG);

  public static final SourceProvider INSTANCE = new SourceProvider(SKIP);

  private final Predicate<String> skip;

  private SourceProvider(Predicate<String> skip) {
    this.skip = skip;
  }

  public Optional<StackTraceElement> get() {
    return get(new Throwable().getStackTrace());
  }

  public Optional<StackTraceElement> get(StackTraceElement[] elements) {
    for (StackTraceElement element : elements) {
      String className = element.getClassName();

      if (!skip.test(className)) {
        int innerStart = className.indexOf('$');
        if (innerStart > 0) {
          return Optional.of(new StackTraceElement(className.substring(0, innerStart),
              element.getMethodName(), element.getFileName(), element.getLineNumber()));
        }
        return Optional.of(element);
      }
    }
    return Optional.empty();
  }

  private static Predicate<String> pkg(String pkg) {
    return classname -> classname.startsWith(pkg);
  }
}
