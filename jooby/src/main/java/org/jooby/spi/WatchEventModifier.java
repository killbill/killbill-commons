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
package org.jooby.spi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.WatchEvent;

public class WatchEventModifier {

  public static WatchEvent.Modifier modifier(String name) {
    try {
      Class e = WatchEventModifier.class.getClassLoader()
          .loadClass("com.sun.nio.file.SensitivityWatchEventModifier");
      Method m = e.getDeclaredMethod("valueOf", String.class);
      return (WatchEvent.Modifier) m.invoke(null, name);
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException x) {
      return () -> name;
    }
  }
}
